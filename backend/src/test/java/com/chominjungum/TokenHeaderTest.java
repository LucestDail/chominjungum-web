package com.chominjungum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 게이트웨이 뒤에서 앱 토큰이 살아남는지.
 *
 * <p>실제 배포에서 겪은 문제: nginx 가 외부 요청에 HTTP Basic 을 요구하면
 * {@code Authorization} 헤더가 Basic 으로 점유되어, 같은 헤더에 실은 Bearer 토큰이 사라진다.
 * 그래서 전용 헤더 {@code X-Auth-Token} 을 함께 지원한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TokenHeaderTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    private String teacherToken() throws Exception {
        String res = mvc.perform(post("/api/auth/teacher/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", "hdr-" + UUID.randomUUID() + "@example.com",
                                "password", "password123",
                                "displayName", "김선생"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(res);
        return node.get("token").asText();
    }

    @Test
    void X_Auth_Token_헤더로_인증된다() throws Exception {
        mvc.perform(get("/api/classrooms").header("X-Auth-Token", teacherToken()))
                .andExpect(status().isOk());
    }

    @Test
    void Bearer_접두사를_붙여도_받아준다() throws Exception {
        mvc.perform(get("/api/classrooms").header("X-Auth-Token", "Bearer " + teacherToken()))
                .andExpect(status().isOk());
    }

    @Test
    void 표준_Authorization_Bearer_도_계속_동작한다() throws Exception {
        mvc.perform(get("/api/classrooms").header("Authorization", "Bearer " + teacherToken()))
                .andExpect(status().isOk());
    }

    @Test
    void Authorization_이_Basic_이어도_전용_헤더로_인증된다() throws Exception {
        // 게이트웨이가 Basic 을 요구하는 상황 그대로
        mvc.perform(get("/api/classrooms")
                        .header("Authorization", "Basic aGFydTpzZWNyZXQ=")
                        .header("X-Auth-Token", teacherToken()))
                .andExpect(status().isOk());
    }

    @Test
    void 잘못된_토큰은_거부된다() throws Exception {
        mvc.perform(get("/api/classrooms").header("X-Auth-Token", "not-a-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 전용_헤더가_비어_있으면_Authorization_으로_넘어간다() throws Exception {
        String token = teacherToken();
        var result = mvc.perform(get("/api/classrooms")
                        .header("X-Auth-Token", "")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }
}
