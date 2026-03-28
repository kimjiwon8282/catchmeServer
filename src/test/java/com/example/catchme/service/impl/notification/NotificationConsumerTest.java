package com.example.catchme.service.impl.notification;

import com.example.catchme.dto.notification.NotificationEvent;
import com.example.catchme.exception.exceptions.ExternalApiException;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {

    @InjectMocks
    private NotificationConsumer notificationConsumer;

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @Nested
    @DisplayName("receiveRiskAlert")
    class ReceiveRiskAlert {

        @Test
        @DisplayName("알림 이벤트를 받으면 Firebase send를 호출한다") //알림 이벤트가 오면 실제로 Firebase를 호출하는지 확인
        void receiveRiskAlertSendsFirebaseMessageSuccessfully() throws Exception {
            NotificationEvent event = new NotificationEvent("fcm-token-123", "지원");

            try (MockedStatic<FirebaseMessaging> firebaseMessagingStatic = mockStatic(FirebaseMessaging.class)) {
                firebaseMessagingStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
                when(firebaseMessaging.send(any(Message.class))).thenReturn("message-id-123");

                notificationConsumer.receiveRiskAlert(event);

                verify(firebaseMessaging).send(any(Message.class));
            }
        }

        @Test
        @DisplayName("Firebase 전송 실패 시 ExternalApiException으로 감싸서 던진다")
        void receiveRiskAlertWrapsExceptionWhenFirebaseSendFails() throws Exception {
            NotificationEvent event = new NotificationEvent("fcm-token-123", "지원");

            try (MockedStatic<FirebaseMessaging> firebaseMessagingStatic = mockStatic(FirebaseMessaging.class)) {
                firebaseMessagingStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
                when(firebaseMessaging.send(any(Message.class)))
                        .thenThrow(new IllegalStateException("fcm send failed"));//Firebase전송 실패 가정

                assertThatThrownBy(() -> notificationConsumer.receiveRiskAlert(event))
                        .isInstanceOf(ExternalApiException.class)
                        .hasMessage("알림 전송 실패");

                verify(firebaseMessaging).send(any(Message.class));
            }
        }
    }
}