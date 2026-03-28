package com.example.catchme.service.impl.ai;

import com.example.catchme.dto.AiPredictionResponse;
import com.example.catchme.dto.notification.NotificationEvent;
import com.example.catchme.model.AiPredictionResult;
import com.example.catchme.model.RawDataFile;
import com.example.catchme.model.Role;
import com.example.catchme.model.User;
import com.example.catchme.repository.AiPredictionResultRepository;
import com.example.catchme.repository.RawDataFileRepository;
import com.example.catchme.repository.UserRepository;
import com.example.catchme.service.impl.notification.NotificationProducer;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PredictionResultServiceTest {

    @Mock
    private RawDataFileRepository rawDataFileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AiPredictionResultRepository aiPredictionResultRepository;

    @Mock
    private NotificationProducer notificationProducer;

    @InjectMocks
    private PredictionResultService predictionResultService;

    @Nested
    @DisplayName("processResult")
    class ProcessResult {

        @Test
        @DisplayName("분석 결과를 저장하고 raw data를 analyzed 처리한다")
        void processResultSavesPredictionAndMarksRawDataAnalyzed() { //정상 저장 + 비위험군 테스트
            // given
            Long rawDataFileId = 100L;
            Long userId = 1L;
            User user = user(1L, "user@catchme.com", "encoded", "지원", Role.USER);
            RawDataFile rawDataFile = rawDataFile(100L, user, "raw-data/user-1/file.csv");
            AiPredictionResponse response = new AiPredictionResponse(2, false, 87.5);

            when(rawDataFileRepository.findById(rawDataFileId)).thenReturn(Optional.of(rawDataFile));
            when(userRepository.findByIdWithLinkedUser(userId)).thenReturn(Optional.of(user));

            ArgumentCaptor<AiPredictionResult> resultCaptor = ArgumentCaptor.forClass(AiPredictionResult.class);

            // when
            predictionResultService.processResult(rawDataFileId, userId, response);

            // then
            assertThat(rawDataFile.isAnalyzed()).isTrue();

            verify(aiPredictionResultRepository).save(resultCaptor.capture());
            AiPredictionResult savedResult = resultCaptor.getValue();

            assertThat(savedResult.getUser()).isEqualTo(user);
            assertThat(savedResult.getRawDataFile()).isEqualTo(rawDataFile);
            assertThat(savedResult.getClusterId()).isEqualTo(2);
            assertThat(savedResult.getIsRisk()).isFalse();
            assertThat(savedResult.getConfidence()).isEqualTo(87.5);
            assertThat(savedResult.getAnalyzedAt()).isNotNull();

            verify(notificationProducer, never()).sendNotificationEvent(org.mockito.ArgumentMatchers.any(NotificationEvent.class)); //비위험군 이므로 알림 안보냄
        }

        @Test
        @DisplayName("위험군이고 보호자 토큰이 있으면 알림 이벤트를 발행한다")
        void processResultSendsNotificationWhenRiskAndGuardianHasToken() {
            // given
            Long rawDataFileId = 100L;
            Long userId = 1L;

            User guardian = user(2L, "guardian@catchme.com", "encoded", "보호자", Role.GUARDIAN);
            guardian.updateFcmToken("guardian-fcm-token");

            User patient = user(1L, "user@catchme.com", "encoded", "지원", Role.USER);
            patient.setLinkedUser(guardian);

            RawDataFile rawDataFile = rawDataFile(100L, patient, "raw-data/user-1/file.csv");
            AiPredictionResponse response = new AiPredictionResponse(1, true, 98.5);

            when(rawDataFileRepository.findById(rawDataFileId)).thenReturn(Optional.of(rawDataFile));
            when(userRepository.findByIdWithLinkedUser(userId)).thenReturn(Optional.of(patient));

            ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);

            // when
            predictionResultService.processResult(rawDataFileId, userId, response);

            // then
            assertThat(rawDataFile.isAnalyzed()).isTrue();

            verify(aiPredictionResultRepository).save(org.mockito.ArgumentMatchers.any(AiPredictionResult.class));
            verify(notificationProducer).sendNotificationEvent(eventCaptor.capture());

            NotificationEvent event = eventCaptor.getValue();
            assertThat(event.getFcmToken()).isEqualTo("guardian-fcm-token");
            assertThat(event.getPatientName()).isEqualTo("지원");
        }

        @Test
        @DisplayName("위험군이어도 보호자가 없거나 보호자 토큰이 없으면 알림을 발행하지 않는다")
        void processResultDoesNotSendNotificationWhenGuardianMissingOrTokenMissing() {
            // given
            Long rawDataFileId = 100L;
            Long userId = 1L;
            User patient = user(1L, "user@catchme.com", "encoded", "지원", Role.USER);
            RawDataFile rawDataFile = rawDataFile(100L, patient, "raw-data/user-1/file.csv");
            AiPredictionResponse response = new AiPredictionResponse(1, true, 92.3);

            when(rawDataFileRepository.findById(rawDataFileId)).thenReturn(Optional.of(rawDataFile));
            when(userRepository.findByIdWithLinkedUser(userId)).thenReturn(Optional.of(patient));

            // when
            predictionResultService.processResult(rawDataFileId, userId, response);

            // then
            assertThat(rawDataFile.isAnalyzed()).isTrue();
            verify(aiPredictionResultRepository).save(org.mockito.ArgumentMatchers.any(AiPredictionResult.class));
            verify(notificationProducer, never()).sendNotificationEvent(org.mockito.ArgumentMatchers.any(NotificationEvent.class));
        }

        @Test
        @DisplayName("raw data file이 없으면 예외를 던지고 이후 작업을 진행하지 않는다")
        void processResultFailsWhenRawDataFileNotFound() {
            // given
            Long rawDataFileId = 100L;
            Long userId = 1L;
            AiPredictionResponse response = new AiPredictionResponse(1, true, 98.5);

            when(rawDataFileRepository.findById(rawDataFileId)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> predictionResultService.processResult(rawDataFileId, userId, response))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("파일을 찾을 수 없습니다.");

            verify(userRepository, never()).findByIdWithLinkedUser(userId);
            verify(aiPredictionResultRepository, never()).save(org.mockito.ArgumentMatchers.any(AiPredictionResult.class));
            verify(notificationProducer, never()).sendNotificationEvent(org.mockito.ArgumentMatchers.any(NotificationEvent.class));
        }

        @Test
        @DisplayName("사용자가 없으면 예외를 던지고 분석 완료 처리나 결과 저장을 하지 않는다")
        void processResultFailsWhenUserNotFound() {
            // given
            Long rawDataFileId = 100L;
            Long userId = 1L;
            User user = user(1L, "user@catchme.com", "encoded", "지원", Role.USER);
            RawDataFile rawDataFile = rawDataFile(100L, user, "raw-data/user-1/file.csv");
            AiPredictionResponse response = new AiPredictionResponse(1, true, 98.5);

            when(rawDataFileRepository.findById(rawDataFileId)).thenReturn(Optional.of(rawDataFile));
            when(userRepository.findByIdWithLinkedUser(userId)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> predictionResultService.processResult(rawDataFileId, userId, response))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("사용자를 찾을 수 없습니다.");

            assertThat(rawDataFile.isAnalyzed()).isFalse();
            verify(aiPredictionResultRepository, never()).save(org.mockito.ArgumentMatchers.any(AiPredictionResult.class));
            verify(notificationProducer, never()).sendNotificationEvent(org.mockito.ArgumentMatchers.any(NotificationEvent.class));
        }
    }

    private User user(Long id, String email, String password, String name, Role role) {
        User user = User.builder()
                .email(email)
                .password(password)
                .name(name)
                .role(role)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private RawDataFile rawDataFile(Long id, User user, String s3ObjectKey) {
        RawDataFile rawDataFile = RawDataFile.create(user, s3ObjectKey);
        ReflectionTestUtils.setField(rawDataFile, "id", id);
        return rawDataFile;
    }
}
