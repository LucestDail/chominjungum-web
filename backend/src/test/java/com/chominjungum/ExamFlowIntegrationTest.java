package com.chominjungum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 교사 가입 → 학급 → 문항 → 과제 개설 → 학생 참여 → 응시 → 서버 재채점까지 한 번에 통과시킨다.
 *
 * <p>실제 PostgreSQL 이 필요하다: `docker compose up -d`
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExamFlowIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    private String uniqueEmail() {
        return "teacher-" + UUID.randomUUID() + "@example.com";
    }

    private JsonNode postJson(String url, String token, Object body) throws Exception {
        var req = post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (token != null) {
            req = req.header("Authorization", "Bearer " + token);
        }
        String response = mvc.perform(req).andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    @Test
    void 출제부터_응시_재채점까지_한_사이클() throws Exception {
        // 1. 교사 가입
        JsonNode teacher = postJson("/api/auth/teacher/register", null,
                Map.of("email", uniqueEmail(), "password", "password123", "displayName", "김선생"));
        String teacherToken = teacher.get("token").asText();
        assertThat(teacher.get("role").asText()).isEqualTo("TEACHER");

        // 2. 학급 개설 — 참여코드가 발급된다
        JsonNode classroom = postJson("/api/classrooms", teacherToken,
                Map.of("name", "1학년 3반", "grade", 1, "schoolYear", 2026));
        String joinCode = classroom.get("joinCode").asText();
        String classroomId = classroom.get("id").asText();
        assertThat(joinCode).hasSize(6);

        // 3. 문항 등록
        JsonNode item1 = postJson("/api/items", teacherToken, Map.of("expectedText", "안녕하세요"));
        JsonNode item2 = postJson("/api/items", teacherToken, Map.of("expectedText", "강아지"));
        assertThat(item1.get("glyphCount").asInt()).isEqualTo(5);

        // 4. 과제 개설 후 열기
        JsonNode assignment = postJson("/api/assignments", teacherToken, Map.of(
                "classroomId", classroomId,
                "title", "2학기 1회",
                "mode", "ONLINE",
                "itemIds", List.of(item1.get("id").asText(), item2.get("id").asText())));
        assertThat(assignment.get("open").asBoolean()).isFalse();

        JsonNode opened = postJson("/api/assignments/" + assignment.get("id").asText() + "/open",
                teacherToken, Map.of());
        assertThat(opened.get("open").asBoolean()).isTrue();

        // 5. 학생 입장 — 계정 없이 참여코드 + 이름
        JsonNode student = postJson("/api/auth/join", null,
                Map.of("joinCode", joinCode, "displayName", "3번 이학생"));
        String studentToken = student.get("token").asText();
        assertThat(student.get("role").asText()).isEqualTo("STUDENT");

        // 6. 응시 화면 — 정답 텍스트는 내려오지 않는다
        String examBody = mvc.perform(get("/api/exam/current").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        assertThat(examBody).doesNotContain("안녕하세요");
        assertThat(examBody).doesNotContain("강아지");

        // 7. 제출 — 서버가 재채점한다
        JsonNode result = postJson("/api/exam/attempts", studentToken, Map.of(
                "itemId", item1.get("id").asText(),
                "rawAnswer", "안녕하세오",
                "inputKind", "keyboard"));

        assertThat(result.get("correctCount").asInt()).isEqualTo(4);
        assertThat(result.get("totalCount").asInt()).isEqualTo(5);
        assertThat(result.get("scorePercent").asInt()).isEqualTo(80);
        assertThat(result.get("expectedText").asText()).isEqualTo("안녕하세요"); // 제출 후 공개
        assertThat(result.get("matches").get(4).get("correct").asBoolean()).isFalse();
    }

    @Test
    void 클라이언트가_보낸_점수를_믿지_않는다() throws Exception {
        JsonNode teacher = postJson("/api/auth/teacher/register", null,
                Map.of("email", uniqueEmail(), "password", "password123", "displayName", "김선생"));
        String teacherToken = teacher.get("token").asText();

        JsonNode classroom = postJson("/api/classrooms", teacherToken, Map.of("name", "2반"));
        JsonNode item = postJson("/api/items", teacherToken, Map.of("expectedText", "학교"));
        JsonNode assignment = postJson("/api/assignments", teacherToken, Map.of(
                "classroomId", classroom.get("id").asText(),
                "title", "쪽지시험",
                "itemIds", List.of(item.get("id").asText())));
        postJson("/api/assignments/" + assignment.get("id").asText() + "/open", teacherToken, Map.of());

        JsonNode student = postJson("/api/auth/join", null,
                Map.of("joinCode", classroom.get("joinCode").asText(), "displayName", "1번"));

        // 점수 필드를 보내도 무시된다 — 서버가 직접 채점한다
        JsonNode result = postJson("/api/exam/attempts", student.get("token").asText(), Map.of(
                "itemId", item.get("id").asText(),
                "rawAnswer", "학꾜",
                "correctCount", 999,
                "totalCount", 999));

        assertThat(result.get("correctCount").asInt()).isEqualTo(1);
        assertThat(result.get("totalCount").asInt()).isEqualTo(2);
    }

    @Test
    void 같은_문항_재제출은_덮어쓴다() throws Exception {
        JsonNode teacher = postJson("/api/auth/teacher/register", null,
                Map.of("email", uniqueEmail(), "password", "password123", "displayName", "김선생"));
        String teacherToken = teacher.get("token").asText();
        JsonNode classroom = postJson("/api/classrooms", teacherToken, Map.of("name", "3반"));
        JsonNode item = postJson("/api/items", teacherToken, Map.of("expectedText", "나무"));
        JsonNode assignment = postJson("/api/assignments", teacherToken, Map.of(
                "classroomId", classroom.get("id").asText(),
                "title", "연습",
                "itemIds", List.of(item.get("id").asText())));
        postJson("/api/assignments/" + assignment.get("id").asText() + "/open", teacherToken, Map.of());

        JsonNode student = postJson("/api/auth/join", null,
                Map.of("joinCode", classroom.get("joinCode").asText(), "displayName", "1번"));
        String studentToken = student.get("token").asText();

        JsonNode first = postJson("/api/exam/attempts", studentToken,
                Map.of("itemId", item.get("id").asText(), "rawAnswer", "나무"));
        JsonNode second = postJson("/api/exam/attempts", studentToken,
                Map.of("itemId", item.get("id").asText(), "rawAnswer", "너무"));

        // 같은 attempt 가 갱신된다(행이 늘지 않는다)
        assertThat(second.get("attemptId").asText()).isEqualTo(first.get("attemptId").asText());
        assertThat(second.get("correctCount").asInt()).isEqualTo(1);
    }

    @Test
    void 교사_토큰_없이는_관리_API_에_접근할_수_없다() throws Exception {
        mvc.perform(get("/api/classrooms")).andExpect(status().isUnauthorized());
    }

    @Test
    void 학생_토큰으로_교사_API_에_접근할_수_없다() throws Exception {
        JsonNode teacher = postJson("/api/auth/teacher/register", null,
                Map.of("email", uniqueEmail(), "password", "password123", "displayName", "김선생"));
        JsonNode classroom = postJson("/api/classrooms", teacher.get("token").asText(),
                Map.of("name", "4반"));
        JsonNode student = postJson("/api/auth/join", null,
                Map.of("joinCode", classroom.get("joinCode").asText(), "displayName", "1번"));

        mvc.perform(get("/api/classrooms").header("Authorization", "Bearer " + student.get("token").asText()))
                .andExpect(status().isForbidden());
    }

    @Test
    void 허용_밖_문자는_문항으로_등록되지_않는다() throws Exception {
        JsonNode teacher = postJson("/api/auth/teacher/register", null,
                Map.of("email", uniqueEmail(), "password", "password123", "displayName", "김선생"));

        mvc.perform(post("/api/items")
                        .header("Authorization", "Bearer " + teacher.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("expectedText", "Hello"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "한글이 아니거나, 완성된 한글 단어가 아닌 문자가 포함되어있습니다."));
    }

    @Test
    void 열일곱글자_문항은_거부된다() throws Exception {
        JsonNode teacher = postJson("/api/auth/teacher/register", null,
                Map.of("email", uniqueEmail(), "password", "password123", "displayName", "김선생"));

        mvc.perform(post("/api/items")
                        .header("Authorization", "Bearer " + teacher.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("expectedText", "가나다라마바사아자차카타파하거너더"))))
                .andExpect(status().isBadRequest());
    }
}
