package com.example.catchme.service.impl.ai;

import com.example.catchme.dto.ai.AiPredictionRequestEvent;
import com.example.catchme.exception.exceptions.UserNotFoundException;
import com.example.catchme.model.RawDataFile;
import com.example.catchme.model.Role;
import com.example.catchme.model.User;
import com.example.catchme.repository.RawDataFileRepository;
import com.example.catchme.repository.UserRepository;
import io.awspring.cloud.sqs.operations.SqsSendOptions;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PredictionServiceImplTest {

    @Mock
    private RawDataFileRepository rawDataFileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SqsTemplate sqsTemplate;

    @Mock
    private SqsSendOptions<AiPredictionRequestEvent> sqsSendOptions;

    @InjectMocks
    private PredictionServiceImpl predictionService;

    @Nested
    @DisplayName("requestLatestPrediction")
    class RequestLatestPrediction {

        @Test
        @DisplayName("최신 raw data가 존재하고 아직 미분석 상태면 AI 분석 요청 이벤트를 SQS 큐에 발행하고 안내 문구를 반환한다")
        void requestLatestPredictionSuccess() {
            Long userId = 1L;
            User user = user(userId, "user@catchme.com", Role.USER);
            RawDataFile rawDataFile = rawDataFile(10L, user, "raw-data/user-1/file.csv", false);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(rawDataFileRepository.findTopByUserOrderByCreatedAtDesc(user)).thenReturn(Optional.of(rawDataFile));
            when(sqsSendOptions.queue(anyString())).thenReturn(sqsSendOptions);
            when(sqsSendOptions.payload(any(AiPredictionRequestEvent.class))).thenReturn(sqsSendOptions);

            String response = predictionService.requestLatestPrediction(userId);

            assertThat(response).isEqualTo("AI 분석 요청이 성공적으로 접수되었습니다. 완료 시 푸시 알림으로 알려드립니다.");

            ArgumentCaptor<Consumer<SqsSendOptions<AiPredictionRequestEvent>>> captor = ArgumentCaptor.forClass(Consumer.class);
            verify(sqsTemplate).send(captor.capture());

            Consumer<SqsSendOptions<AiPredictionRequestEvent>> consumer = captor.getValue();
            consumer.accept(sqsSendOptions);

            verify(sqsSendOptions).queue("ai-prediction-request-queue");
            ArgumentCaptor<AiPredictionRequestEvent> eventCaptor = ArgumentCaptor.forClass(AiPredictionRequestEvent.class);
            verify(sqsSendOptions).payload(eventCaptor.capture());

            AiPredictionRequestEvent event = eventCaptor.getValue();
            assertThat(event.getUserId()).isEqualTo(1L);
            assertThat(event.getRawDataFileId()).isEqualTo(10L);
            assertThat(event.getS3ObjectKey()).isEqualTo("raw-data/user-1/file.csv");
        }

        @Test
        @DisplayName("사용자가 없으면 UserNotFoundException을 던지고 raw data 조회나 SQS 발행을 진행하지 않는다")
        void requestLatestPredictionFailsWhenUserNotFound() {
            Long userId = 999L;
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> predictionService.requestLatestPrediction(userId))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessage("사용자를 찾을 수 없습니다.");

            verify(rawDataFileRepository, never()).findTopByUserOrderByCreatedAtDesc(any(User.class));
            verify(sqsTemplate, never()).send(any());
        }

        @Test
        @DisplayName("최신 측정 데이터가 없으면 IllegalStateException을 던지고 SQS 발행을 진행하지 않는다")
        void requestLatestPredictionFailsWhenRawDataDoesNotExist() {
            Long userId = 1L;
            User user = user(userId, "user@catchme.com", Role.USER);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(rawDataFileRepository.findTopByUserOrderByCreatedAtDesc(user)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> predictionService.requestLatestPrediction(userId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("측정 데이터가 없습니다.");

            verify(sqsTemplate, never()).send(any());
        }

        @Test
        @DisplayName("최신 측정 데이터가 이미 분석 완료 상태면 IllegalStateException을 던지고 SQS 발행을 진행하지 않는다")
        void requestLatestPredictionFailsWhenRawDataAlreadyAnalyzed() {
            Long userId = 1L;
            User user = user(userId, "user@catchme.com", Role.USER);
            RawDataFile rawDataFile = rawDataFile(10L, user, "raw-data/user-1/file.csv", true);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(rawDataFileRepository.findTopByUserOrderByCreatedAtDesc(user)).thenReturn(Optional.of(rawDataFile));

            assertThatThrownBy(() -> predictionService.requestLatestPrediction(userId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("이미 분석이 완료된 데이터입니다.");

            verify(sqsTemplate, never()).send(any());
        }
    }

    private User user(Long id, String email, Role role) {
        User user = User.builder()
                .email(email)
                .password("encodedPassword")
                .name("지원")
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
