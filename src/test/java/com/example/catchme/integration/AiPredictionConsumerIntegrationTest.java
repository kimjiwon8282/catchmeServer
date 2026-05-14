package com.example.catchme.integration;

import com.example.catchme.dto.ai.AiPredictionRequestEvent;
import com.example.catchme.dto.notification.NotificationEvent;
import com.example.catchme.model.AiPredictionResult;
import com.example.catchme.model.RawDataFile;
import com.example.catchme.model.Role;
import com.example.catchme.model.User;
import com.example.catchme.repository.AiPredictionResultRepository;
import com.example.catchme.repository.RawDataFileRepository;
import com.example.catchme.repository.UserRepository;
import com.example.catchme.service.impl.ai.AiPredictionConsumer;
import com.example.catchme.service.impl.ai.PredictionResultService;
import com.example.catchme.service.impl.notification.NotificationProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(classes = {
        AiPredictionConsumer.class,
        PredictionResultService.class,
        AiPredictionConsumerIntegrationTest.MockConfig.class
})
class AiPredictionConsumerIntegrationTest {

    @Autowired
    private AiPredictionConsumer aiPredictionConsumer;

    @Autowired
    private RawDataFileRepository rawDataFileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AiPredictionResultRepository aiPredictionResultRepository;

    @Autowired
    private NotificationProducer notificationProducer;

    @BeforeEach
    void setUp() {
        reset(rawDataFileRepository, userRepository, aiPredictionResultRepository, notificationProducer);
    }

    @Test
    @DisplayName("큐 이벤트를 소비하면 raw data를 analyzed 처리하고 결과를 저장한 뒤 위험군 알림을 발행한다")
    void processAiPrediction_shouldPersistResultAndSendNotification_whenRiskAndGuardianHasToken() {
        Long userId = 1L;
        Long rawDataFileId = 100L;
        AiPredictionRequestEvent event = new AiPredictionRequestEvent(userId, rawDataFileId, "raw-data/user-1/file.csv");

        User guardian = user(2L, "guardian@catchme.com", "encoded", "보호자", Role.GUARDIAN);
        guardian.updateFcmToken("guardian-fcm-token");

        User patient = user(1L, "user@catchme.com", "encoded", "지원", Role.USER);
        patient.setLinkedUser(guardian);

        RawDataFile rawDataFile = rawDataFile(rawDataFileId, patient, "raw-data/user-1/file.csv");

        when(rawDataFileRepository.findById(rawDataFileId)).thenReturn(Optional.of(rawDataFile));
        when(userRepository.findByIdWithLinkedUser(userId)).thenReturn(Optional.of(patient));

        ArgumentCaptor<AiPredictionResult> resultCaptor = ArgumentCaptor.forClass(AiPredictionResult.class);
        ArgumentCaptor<NotificationEvent> notificationCaptor = ArgumentCaptor.forClass(NotificationEvent.class);

        aiPredictionConsumer.processAiPrediction(event);

        assertThat(rawDataFile.isAnalyzed()).isTrue();

        verify(aiPredictionResultRepository).save(resultCaptor.capture());
        AiPredictionResult savedResult = resultCaptor.getValue();
        assertThat(savedResult.getUser()).isEqualTo(patient);
        assertThat(savedResult.getRawDataFile()).isEqualTo(rawDataFile);
        assertThat(savedResult.getClusterId()).isEqualTo(1);
        assertThat(savedResult.getIsRisk()).isTrue();
        assertThat(savedResult.getConfidence()).isEqualTo(98.5);
        assertThat(savedResult.getAnalyzedAt()).isNotNull();

        verify(notificationProducer).sendNotificationEvent(notificationCaptor.capture());
        NotificationEvent notificationEvent = notificationCaptor.getValue();
        assertThat(notificationEvent.getFcmToken()).isEqualTo("guardian-fcm-token");
        assertThat(notificationEvent.getPatientName()).isEqualTo("지원");
    }

    @Test
    @DisplayName("위험군이어도 보호자 토큰이 없으면 결과만 저장하고 알림은 발행하지 않는다")
    void processAiPrediction_shouldSaveOnly_whenGuardianTokenMissing() {
        Long userId = 1L;
        Long rawDataFileId = 100L;
        AiPredictionRequestEvent event = new AiPredictionRequestEvent(userId, rawDataFileId, "raw-data/user-1/file.csv");

        User guardian = user(2L, "guardian@catchme.com", "encoded", "보호자", Role.GUARDIAN);

        User patient = user(1L, "user@catchme.com", "encoded", "지원", Role.USER);
        patient.setLinkedUser(guardian);

        RawDataFile rawDataFile = rawDataFile(rawDataFileId, patient, "raw-data/user-1/file.csv");

        when(rawDataFileRepository.findById(rawDataFileId)).thenReturn(Optional.of(rawDataFile));
        when(userRepository.findByIdWithLinkedUser(userId)).thenReturn(Optional.of(patient));

        ArgumentCaptor<AiPredictionResult> resultCaptor = ArgumentCaptor.forClass(AiPredictionResult.class);

        aiPredictionConsumer.processAiPrediction(event);

        assertThat(rawDataFile.isAnalyzed()).isTrue();

        verify(aiPredictionResultRepository).save(resultCaptor.capture());
        AiPredictionResult savedResult = resultCaptor.getValue();
        assertThat(savedResult.getClusterId()).isEqualTo(1);
        assertThat(savedResult.getIsRisk()).isTrue();
        assertThat(savedResult.getConfidence()).isEqualTo(98.5);

        verify(notificationProducer, never()).sendNotificationEvent(any(NotificationEvent.class));
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

    @TestConfiguration
    static class MockConfig {
        @Bean
        RawDataFileRepository rawDataFileRepository() {
            return mock(RawDataFileRepository.class);
        }

        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        AiPredictionResultRepository aiPredictionResultRepository() {
            return mock(AiPredictionResultRepository.class);
        }

        @Bean
        NotificationProducer notificationProducer() {
            return mock(NotificationProducer.class);
        }
    }
}
