package com.example.catchme.integration;

import com.example.catchme.config.auth.JwtAccessDeniedHandler;
import com.example.catchme.config.auth.JwtAuthenticationEntryPoint;
import com.example.catchme.config.auth.SecurityConfig;
import com.example.catchme.config.auth.TokenProvider;
import com.example.catchme.controller.PredictionController;
import com.example.catchme.dto.ai.AiPredictionRequestEvent;
import com.example.catchme.exception.GlobalExceptionHandler;
import com.example.catchme.model.RawDataFile;
import com.example.catchme.model.Role;
import com.example.catchme.model.User;
import com.example.catchme.repository.RawDataFileRepository;
import com.example.catchme.repository.UserRepository;
import com.example.catchme.service.impl.ai.PredictionServiceImpl;
import com.example.catchme.service.interfaces.user.PredictionReadService;
import io.awspring.cloud.sqs.operations.SqsSendOptions;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SQS 발행 통합 테스트
 *
 * 목적:
 * 1) /api/predictions/latest 요청이 실제 SecurityFilterChain 을 통과하는지
 * 2) 컨트롤러 -> 서비스 -> SqsTemplate.send() 흐름이 한 번에 이어지는지
 * 3) USER/GUARDIAN 권한 분리가 실제 엔드포인트에서 올바르게 작동하는지
 * 4) 데이터가 없거나 이미 분석된 경우 SQS 발행 없이 예외 응답이 내려가는지
 *
 * 특징:
 * - WebMvcTest + 실제 SecurityConfig 사용
 * - PredictionService 는 mock 이 아니라 실제 PredictionServiceImpl 사용
 * - DB 대신 Repository/SqsTemplate 만 mock 처리
 */
@WebMvcTest(controllers = PredictionController.class)
@Import({
        PredictionServiceImpl.class,
        SecurityConfig.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class,
        GlobalExceptionHandler.class
})
class PredictionSqsPublishIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PredictionReadService predictionReadService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private RawDataFileRepository rawDataFileRepository;

    @MockitoBean
    private SqsTemplate sqsTemplate;

    @MockitoBean
    private TokenProvider tokenProvider;

    @MockitoBean
    private SqsSendOptions<AiPredictionRequestEvent> sqsSendOptions;

    @Test
    @DisplayName("USER 가 최신 분석 요청을 보내면 202 Accepted 와 함께 SQS 큐에 이벤트를 발행한다")
    void predictLatest_shouldPublishEventAndReturn202() throws Exception {
        Long userId = 1L;
        User user = user(userId, "user@test.com", Role.USER);
        RawDataFile rawDataFile = rawDataFile(10L, user, "raw-data/user-1/latest.csv", false);

        mockAuthenticatedUser(Role.USER, userId, "user@test.com");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(rawDataFileRepository.findTopByUserOrderByCreatedAtDesc(user)).thenReturn(Optional.of(rawDataFile));
        when(sqsSendOptions.queue(anyString())).thenReturn(sqsSendOptions);
        when(sqsSendOptions.payload(any(AiPredictionRequestEvent.class))).thenReturn(sqsSendOptions);

        mockMvc.perform(post("/api/predictions/latest")
                        .header("Authorization", "Bearer valid-user-token"))
                .andExpect(status().isAccepted())
                .andExpect(content().string("AI 분석 요청이 성공적으로 접수되었습니다. 완료 시 푸시 알림으로 알려드립니다."));

        Consumer<SqsSendOptions<AiPredictionRequestEvent>> consumer = extractCapturedSendConsumer();
        consumer.accept(sqsSendOptions);

        verify(sqsSendOptions).queue("ai-prediction-request-queue");
        verify(sqsSendOptions).payload(any(AiPredictionRequestEvent.class));
    }

    @Test
    @DisplayName("GUARDIAN 은 /api/predictions/latest 접근 시 403 이고 SQS 발행도 일어나지 않는다")
    void predictLatest_shouldReturn403_whenGuardianRequests() throws Exception {
        mockAuthenticatedUser(Role.GUARDIAN, 2L, "guardian@test.com");

        mockMvc.perform(post("/api/predictions/latest")
                        .header("Authorization", "Bearer valid-guardian-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        verify(userRepository, never()).findById(any());
        verify(sqsTemplate, never()).send(any());
    }

    @Test
    @DisplayName("최신 raw data 가 없으면 409 를 반환하고 SQS 발행은 하지 않는다")
    void predictLatest_shouldReturn409_whenLatestRawDataMissing() throws Exception {
        Long userId = 1L;
        User user = user(userId, "user@test.com", Role.USER);

        mockAuthenticatedUser(Role.USER, userId, "user@test.com");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(rawDataFileRepository.findTopByUserOrderByCreatedAtDesc(user)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/predictions/latest")
                        .header("Authorization", "Bearer valid-user-token"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("측정 데이터가 없습니다."));

        verify(sqsTemplate, never()).send(any());
    }

    @Test
    @DisplayName("최신 raw data 가 이미 분석 완료 상태면 409 를 반환하고 SQS 발행은 하지 않는다")
    void predictLatest_shouldReturn409_whenLatestRawDataAlreadyAnalyzed() throws Exception {
        Long userId = 1L;
        User user = user(userId, "user@test.com", Role.USER);
        RawDataFile analyzedFile = rawDataFile(10L, user, "raw-data/user-1/latest.csv", true);

        mockAuthenticatedUser(Role.USER, userId, "user@test.com");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(rawDataFileRepository.findTopByUserOrderByCreatedAtDesc(user)).thenReturn(Optional.of(analyzedFile));

        mockMvc.perform(post("/api/predictions/latest")
                        .header("Authorization", "Bearer valid-user-token"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("이미 분석이 완료된 데이터입니다."));

        verify(sqsTemplate, never()).send(any());
    }

    @SuppressWarnings("unchecked")
    private Consumer<SqsSendOptions<AiPredictionRequestEvent>> extractCapturedSendConsumer() {
        org.mockito.ArgumentCaptor<Consumer<SqsSendOptions<AiPredictionRequestEvent>>> captor =
                org.mockito.ArgumentCaptor.forClass(Consumer.class);
        verify(sqsTemplate).send(captor.capture());
        Consumer<SqsSendOptions<AiPredictionRequestEvent>> consumer = captor.getValue();
        assertThat(consumer).isNotNull();
        return consumer;
    }

    private void mockAuthenticatedUser(Role role, Long userId, String email) {
        User principal = user(userId, email, role);

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal,
                "mock-jwt-token",
                principal.getAuthorities()
        );

        when(tokenProvider.validateToken(any())).thenReturn(true);
        when(tokenProvider.getAuthentication(any())).thenReturn(authentication);
    }

    private User user(Long id, String email, Role role) {
        User user = User.builder()
                .email(email)
                .password("encoded-password")
                .name("테스트유저")
                .role(role)
                .build();

        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private RawDataFile rawDataFile(Long id, User user, String s3ObjectKey, boolean analyzed) {
        RawDataFile rawDataFile = RawDataFile.create(user, s3ObjectKey);
        ReflectionTestUtils.setField(rawDataFile, "id", id);
        if (analyzed) {
            rawDataFile.markAnalyzed();
        }
        return rawDataFile;
    }
}
