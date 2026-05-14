package com.example.catchme.integration;

import com.example.catchme.config.auth.JwtAccessDeniedHandler;
import com.example.catchme.config.auth.JwtAuthenticationEntryPoint;
import com.example.catchme.config.auth.SecurityConfig;
import com.example.catchme.config.auth.TokenProvider;
import com.example.catchme.controller.AuthController;
import com.example.catchme.controller.PredictionController;
import com.example.catchme.controller.TestController;
import com.example.catchme.dto.LoginResponse;
import com.example.catchme.model.Role;
import com.example.catchme.model.User;
import com.example.catchme.service.interfaces.ai.PredictionService;
import com.example.catchme.service.interfaces.auth.AuthService;
import com.example.catchme.service.interfaces.user.PredictionReadService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인증/인가 통합 테스트 1차 버전
 *
 * 목적:
 * 1) 공개 엔드포인트(/api/auth/**)는 토큰 없이 접근 가능한지
 * 2) 보호 엔드포인트는 토큰 없으면 401이 나는지
 * 3) USER / GUARDIAN 권한이 실제 SecurityFilterChain에서 올바르게 막히는지
 * 4) JWT 필터가 SecurityContext에 principal을 넣어주는지
 *
 * 특징:
 * - WebMvcTest + 실제 SecurityFilterChain 사용
 * - DB/Redis/SQS까지 붙이는 풀 통합 테스트 전 단계로 가장 먼저 작성하기 좋은 테스트
 * - TokenProvider만 mock 처리해서 “보안 흐름”에 집중
 */
@WebMvcTest(controllers = { //인증, 인가 흐름만 먼저 확인함
        AuthController.class,
        PredictionController.class,
        TestController.class
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class
})
class AuthAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private PredictionService predictionService;

    @MockitoBean
    private PredictionReadService predictionReadService;

    @MockitoBean
    private TokenProvider tokenProvider;

    @Test
    @DisplayName("로그인 API는 permitAll 이므로 토큰 없이 호출 가능하다")
    void loginEndpoint_shouldBeAccessibleWithoutToken() throws Exception {
        LoginResponse response = new LoginResponse("mock-access-token", "USER");
        when(authService.login(any())).thenReturn(response);

        String requestBody = """
                {
                  "email": "user@test.com",
                  "password": "1234",
                  "fcmToken": "fcm-token-123"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mock-access-token"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @DisplayName("보호 엔드포인트는 토큰이 없으면 401을 반환한다")
    void protectedEndpoint_shouldReturn401_whenNoToken() throws Exception {
        mockMvc.perform(get("/api/test/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("USER 권한은 /api/predictions/history/me 접근 가능")
    void userRole_shouldAccessMyHistory() throws Exception {
        mockAuthenticatedUser(Role.USER, 1L, "user@test.com");
        when(predictionReadService.getMyHistory(any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/predictions/history/me")
                        .header("Authorization", "Bearer valid-user-token"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GUARDIAN 권한은 /api/predictions/history/me 접근 시 403")
    void guardianRole_shouldBeForbiddenFromMyHistory() throws Exception {
        mockAuthenticatedUser(Role.GUARDIAN, 2L, "guardian@test.com");

        mockMvc.perform(get("/api/predictions/history/me")
                        .header("Authorization", "Bearer valid-guardian-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GUARDIAN 권한은 /api/predictions/history/patient 접근 가능")
    void guardianRole_shouldAccessPatientHistory() throws Exception {
        mockAuthenticatedUser(Role.GUARDIAN, 2L, "guardian@test.com");
        when(predictionReadService.getPatientHistory(any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/predictions/history/patient")
                        .header("Authorization", "Bearer valid-guardian-token"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("USER 권한은 /api/predictions/history/patient 접근 시 403")
    void userRole_shouldBeForbiddenFromPatientHistory() throws Exception {
        mockAuthenticatedUser(Role.USER, 1L, "user@test.com");

        mockMvc.perform(get("/api/predictions/history/patient")
                        .header("Authorization", "Bearer valid-user-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    /**
     * JWT 필터가 사용할 TokenProvider 동작을 mock 처리한다.
     * - validateToken() -> true
     * - getAuthentication() -> principal=User, authorities=ROLE_xxx
     */
    private void mockAuthenticatedUser(Role role, Long userId, String email) {
        User principal = User.builder()
                .email(email)
                .password("encoded-password")
                .name("테스트유저")
                .role(role)
                .build();

        ReflectionTestUtils.setField(principal, "id", userId);

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal,
                "mock-jwt-token",
                principal.getAuthorities()
        );

        when(tokenProvider.validateToken(any())).thenReturn(true);
        when(tokenProvider.getAuthentication(any())).thenReturn(authentication);
    }
}
