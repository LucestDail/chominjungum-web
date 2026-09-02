package com.chominjungum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** 성적·취약 자모 리포트. 실제 PostgreSQL 필요: `docker compose up -d` */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReportIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    private JsonNode postJson(String url, String token, Object body) throws Exception {
        var req = post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (token != null) {
            req = req.header("Authorization", "Bearer " + token);
        }
        String res = mvc.perform(req).andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res);
    }

    private JsonNode getJson(String url, String token) throws Exception {
        String res = mvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res);
    }

    /** 교사 + 학급 + 문항 + 열린 과제 + 응시 학생 하나를 만들어 둔다. */
    private record Setup(String teacherToken, String classroomId, String studentToken, String itemId) {
    }

    private Setup prepare(String expectedText) throws Exception {
        JsonNode teacher = postJson("/api/auth/teacher/register", null, Map.of(
                "email", "t-" + UUID.randomUUID() + "@example.com",
                "password", "password123",
                "displayName", "김선생"));
        String token = teacher.get("token").asText();

        JsonNode classroom = postJson("/api/classrooms", token, Map.of("name", "1학년 3반"));
        JsonNode item = postJson("/api/items", token, Map.of("expectedText", expectedText));
        JsonNode assignment = postJson("/api/assignments", token, Map.of(
                "classroomId", classroom.get("id").asText(),
                "title", "받아쓰기",
                "itemIds", List.of(item.get("id").asText())));
        postJson("/api/assignments/" + assignment.get("id").asText() + "/open", token, Map.of());

        JsonNode student = postJson("/api/auth/join", null, Map.of(
                "joinCode", classroom.get("joinCode").asText(), "displayName", "3번 이OO"));

        return new Setup(token, classroom.get("id").asText(),
                student.get("token").asText(), item.get("id").asText());
    }

    @Test
    void 학급_리포트에_학생별_평균과_문항_정답률이_나온다() throws Exception {
        Setup s = prepare("안녕하세요");
        postJson("/api/exam/attempts", s.studentToken(),
                Map.of("itemId", s.itemId(), "rawAnswer", "안녕하세오"));

        JsonNode report = getJson("/api/reports/classroom/" + s.classroomId(), s.teacherToken());

        assertThat(report.get("classroomName").asText()).isEqualTo("1학년 3반");
        assertThat(report.get("attemptCount").asInt()).isEqualTo(1);
        assertThat(report.get("averagePercent").asInt()).isEqualTo(80);

        JsonNode student = report.get("students").get(0);
        assertThat(student.get("displayName").asText()).isEqualTo("3번 이OO");
        assertThat(student.get("attemptCount").asInt()).isEqualTo(1);
        assertThat(student.get("averagePercent").asInt()).isEqualTo(80);
        assertThat(student.get("perfectCount").asInt()).isZero();

        JsonNode item = report.get("items").get(0);
        assertThat(item.get("expectedText").asText()).isEqualTo("안녕하세요");
        assertThat(item.get("averagePercent").asInt()).isEqualTo(80);
    }

    @Test
    void 틀린_글자의_자모가_취약점으로_집계된다() throws Exception {
        // '요'(ㅇ+ㅛ) 를 '오'(ㅇ+ㅗ) 로 틀리면 그 글자의 자모가 취약점에 잡힌다
        Setup s = prepare("안녕하세요");
        postJson("/api/exam/attempts", s.studentToken(),
                Map.of("itemId", s.itemId(), "rawAnswer", "안녕하세오"));

        JsonNode roster = getJson("/api/classrooms/" + s.classroomId() + "/students", s.teacherToken());
        String studentId = roster.get(0).get("id").asText();

        JsonNode report = getJson("/api/reports/student/" + studentId, s.teacherToken());

        assertThat(report.get("attemptCount").asInt()).isEqualTo(1);
        assertThat(report.get("averagePercent").asInt()).isEqualTo(80);
        assertThat(report.get("attempts").get(0).get("rawAnswer").asText()).isEqualTo("안녕하세오");

        JsonNode weak = report.get("weakJamos");
        assertThat(weak).isNotEmpty();
        // 틀린 글자 '요' 의 중성 ㅛ(0x1172=4465? 실제 코드는 분해 결과를 따른다)가 포함된다
        List<String> kinds = new java.util.ArrayList<>();
        weak.forEach(w -> kinds.add(w.get("kind").asText()));
        assertThat(kinds).contains("jung");
    }

    @Test
    void 만점이면_취약_자모가_없다() throws Exception {
        Setup s = prepare("학교");
        postJson("/api/exam/attempts", s.studentToken(),
                Map.of("itemId", s.itemId(), "rawAnswer", "학교"));

        JsonNode roster = getJson("/api/classrooms/" + s.classroomId() + "/students", s.teacherToken());
        JsonNode report = getJson(
                "/api/reports/student/" + roster.get(0).get("id").asText(), s.teacherToken());

        assertThat(report.get("averagePercent").asInt()).isEqualTo(100);
        assertThat(report.get("weakJamos")).isEmpty();
    }

    @Test
    void 성적표_CSV_를_내려받을_수_있다() throws Exception {
        Setup s = prepare("나무");
        postJson("/api/exam/attempts", s.studentToken(),
                Map.of("itemId", s.itemId(), "rawAnswer", "나무"));

        String csv = mvc.perform(get("/api/reports/classroom/" + s.classroomId() + "/csv")
                        .header("Authorization", "Bearer " + s.teacherToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(csv).contains("학생,제출수,평균점수,만점횟수");
        assertThat(csv).contains("3번 이OO,1,100,1");
    }

    @Test
    void 다른_교사의_학급_리포트는_볼_수_없다() throws Exception {
        Setup mine = prepare("나무");
        Setup other = prepare("학교");

        mvc.perform(get("/api/reports/classroom/" + mine.classroomId())
                        .header("Authorization", "Bearer " + other.teacherToken()))
                .andExpect(status().isForbidden());
    }
}
