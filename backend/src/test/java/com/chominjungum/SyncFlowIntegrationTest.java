package com.chominjungum;

import static org.assertj.core.api.Assertions.assertThat;
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

/**
 * 교실 LAN 수업 → 업싱크 → 성적부 반영. 오프라인 우선 설계의 검증.
 *
 * <p>실제 PostgreSQL 이 필요하다: `docker compose up -d`
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SyncFlowIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    private JsonNode postJson(String url, String token, Object body) throws Exception {
        var req = post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (token != null) {
            req = req.header("Authorization", "Bearer " + token);
        }
        String response = mvc.perform(req).andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private record Fixture(String token, String classroomId) {
    }

    private Fixture setup() throws Exception {
        JsonNode teacher = postJson("/api/auth/teacher/register", null, Map.of(
                "email", "teacher-" + UUID.randomUUID() + "@example.com",
                "password", "password123",
                "displayName", "김선생"));
        String token = teacher.get("token").asText();
        JsonNode classroom = postJson("/api/classrooms", token, Map.of("name", "1학년 3반"));
        return new Fixture(token, classroom.get("id").asText());
    }

    private Map<String, Object> batch(String sessionId, String classroomId, UUID attemptId, String device) {
        return Map.of(
                "sessionId", sessionId,
                "classroomId", classroomId,
                "source", "LAN",
                "startedAtMs", 1756800000000L,
                "endedAtMs", 1756802400000L,
                "items", List.of(Map.of("id", "e3b0c4429", "expectedText", "안녕하세요")),
                "attempts", List.of(Map.of(
                        "attemptId", attemptId.toString(),
                        "itemId", "e3b0c4429",
                        "deviceBindingId", device,
                        "rawAnswer", "안녕하세오",
                        "correctCount", 4,
                        "totalCount", 5,
                        "inputKind", "keyboard",
                        "submittedAtMs", 1756801200000L,
                        "matches", List.of(
                                Map.of("i", 0, "ok", true),
                                Map.of("i", 4, "ok", false, "why", "글자 불일치")))));
    }

    @Test
    void 교실_수업_결과를_업로드하면_성적부에_들어간다() throws Exception {
        Fixture f = setup();
        UUID attemptId = UUID.randomUUID();

        JsonNode res = postJson("/api/sync/sessions", f.token(),
                batch(UUID.randomUUID().toString(), f.classroomId(), attemptId, "device-1"));

        assertThat(res.get("accepted").asInt()).isEqualTo(1);
        assertThat(res.get("duplicated").asInt()).isZero();
        assertThat(res.get("rejected")).isEmpty();
        // 명단에 없는 기기 → 교사에게 연결하라고 알려준다
        assertThat(res.get("unassignedDevices").get(0).asText()).isEqualTo("device-1");
    }

    @Test
    void 같은_배치를_두_번_보내도_중복되지_않는다() throws Exception {
        Fixture f = setup();
        String sessionId = UUID.randomUUID().toString();
        UUID attemptId = UUID.randomUUID();
        var payload = batch(sessionId, f.classroomId(), attemptId, "device-2");

        JsonNode first = postJson("/api/sync/sessions", f.token(), payload);
        JsonNode second = postJson("/api/sync/sessions", f.token(), payload);

        assertThat(first.get("accepted").asInt()).isEqualTo(1);
        assertThat(second.get("accepted").asInt()).isZero();
        assertThat(second.get("duplicated").asInt()).isEqualTo(1);
    }

    @Test
    void 재채점해_새_attemptId_로_다시_보내도_신규로_세지_않는다() throws Exception {
        // 앱은 다시 채점하면 새 attemptId 를 만든다(답이 바뀐 것이므로 새 시도로 본다).
        // 그때 서버는 같은 (세션·기기·문항) 이라 기존 행을 **갱신**하는데,
        // 중복 판정을 attemptId 로 하면 "신규"로 세어 교사가 보는 숫자가 틀린다.
        Fixture f = setup();
        String sessionId = UUID.randomUUID().toString();

        JsonNode first = postJson("/api/sync/sessions", f.token(),
                batch(sessionId, f.classroomId(), UUID.randomUUID(), "device-rescore"));
        JsonNode second = postJson("/api/sync/sessions", f.token(),
                batch(sessionId, f.classroomId(), UUID.randomUUID(), "device-rescore"));

        assertThat(first.get("accepted").asInt()).isEqualTo(1);
        assertThat(second.get("accepted").asInt()).isZero();
        assertThat(second.get("duplicated").asInt()).isEqualTo(1);
    }

    @Test
    void 같은_attemptId_로_다른_문항을_보내도_남의_답안을_덮어쓰지_않는다() throws Exception {
        // attemptId 를 그대로 PK 로 쓰면, 이미 있는 id 로 다른 (세션·기기·문항) 을 보낼 때
        // JPA 가 그 행을 merge 해서 **다른 답안이 이 내용으로 바뀐다**.
        Fixture f = setup();
        UUID shared = UUID.randomUUID();

        postJson("/api/sync/sessions", f.token(),
                batch(UUID.randomUUID().toString(), f.classroomId(), shared, "device-a"));
        JsonNode second = postJson("/api/sync/sessions", f.token(),
                batch(UUID.randomUUID().toString(), f.classroomId(), shared, "device-b"));

        // 기기가 다르므로 새 제출이다 — 덮어쓰기가 아니라 별도 행으로 들어가야 한다
        assertThat(second.get("accepted").asInt()).isEqualTo(1);
        assertThat(second.get("duplicated").asInt()).isZero();

        // 덮어썼다면 기존 행을 찾아 duplicated 로 셌을 것이다 — accepted 가 곧 별도 행의 증거다.
        assertThat(second.get("rejected")).isEmpty();
    }

    @Test
    void 모르는_문항의_제출은_거부되고_나머지는_저장된다() throws Exception {
        Fixture f = setup();

        JsonNode res = postJson("/api/sync/sessions", f.token(), Map.of(
                "sessionId", UUID.randomUUID().toString(),
                "classroomId", f.classroomId(),
                "source", "LAN",
                "items", List.of(Map.of("id", "known", "expectedText", "학교")),
                "attempts", List.of(
                        Map.of("attemptId", UUID.randomUUID().toString(), "itemId", "known",
                                "deviceBindingId", "d1", "rawAnswer", "학교",
                                "submittedAtMs", 1756801200000L),
                        Map.of("attemptId", UUID.randomUUID().toString(), "itemId", "ghost",
                                "deviceBindingId", "d1", "rawAnswer", "무엇",
                                "submittedAtMs", 1756801200000L))));

        assertThat(res.get("accepted").asInt()).isEqualTo(1);
        assertThat(res.get("rejected")).hasSize(1);
        assertThat(res.get("rejected").get(0).get("reason").asText()).isEqualTo("UNKNOWN_ITEM");
    }

    @Test
    void 서버가_다시_채점하므로_앱이_보낸_점수는_무시된다() throws Exception {
        Fixture f = setup();

        postJson("/api/sync/sessions", f.token(), Map.of(
                "sessionId", UUID.randomUUID().toString(),
                "classroomId", f.classroomId(),
                "items", List.of(Map.of("id", "i1", "expectedText", "안녕하세요")),
                "attempts", List.of(Map.of(
                        "attemptId", UUID.randomUUID().toString(),
                        "itemId", "i1",
                        "deviceBindingId", "d9",
                        "rawAnswer", "안녕하세오",
                        "correctCount", 999,
                        "totalCount", 999,
                        "submittedAtMs", 1756801200000L))));

        // 성적은 서버 재채점 값(4/5)으로 남는다 — 아래 매핑 후 조회로 확인
        JsonNode student = postJson("/api/classrooms/" + f.classroomId() + "/students", f.token(),
                Map.of("displayName", "3번"));
        JsonNode bind = postJson("/api/classrooms/" + f.classroomId() + "/devices", f.token(),
                Map.of("deviceBindingId", "d9", "studentId", student.get("id").asText()));

        assertThat(bind.get("backfilled").asInt()).isEqualTo(1);
    }

    @Test
    void 기기를_명단에_연결하면_이전_제출이_소급_매핑된다() throws Exception {
        Fixture f = setup();

        // 먼저 익명 기기로 제출이 올라온다
        postJson("/api/sync/sessions", f.token(),
                batch(UUID.randomUUID().toString(), f.classroomId(), UUID.randomUUID(), "device-late"));

        // 나중에 교사가 기기를 학생과 연결한다
        JsonNode student = postJson("/api/classrooms/" + f.classroomId() + "/students", f.token(),
                Map.of("displayName", "5번 박OO"));
        JsonNode bind = postJson("/api/classrooms/" + f.classroomId() + "/devices", f.token(),
                Map.of("deviceBindingId", "device-late", "studentId", student.get("id").asText()));

        assertThat(bind.get("backfilled").asInt()).isEqualTo(1);
    }

    @Test
    void 다른_교사의_학급으로는_업로드할_수_없다() throws Exception {
        Fixture mine = setup();
        Fixture other = setup();

        mvc.perform(post("/api/sync/sessions")
                        .header("Authorization", "Bearer " + other.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(batch(
                                UUID.randomUUID().toString(), mine.classroomId(), UUID.randomUUID(), "d"))))
                .andExpect(status().isForbidden());
    }
}
