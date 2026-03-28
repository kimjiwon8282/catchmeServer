package com.example.catchme.service.impl.notification;

import com.example.catchme.dto.notification.NotificationEvent;
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

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationProducer 단위 테스트")
class NotificationProducerTest { //어떤 큐에 어떤 메시지를 보내는가 검증함

    @Mock
    private SqsTemplate sqsTemplate;

    @Mock
    private SqsSendOptions<NotificationEvent> sqsSendOptions;

    @InjectMocks
    private NotificationProducer notificationProducer;

    @Nested
    @DisplayName("sendNotificationEvent")
    class SendNotificationEvent {

        @Test
        @DisplayName("알림 이벤트를 notification 큐에 payload로 발행한다")
        void sendNotificationEventSuccess() {
            // given
            NotificationEvent event = new NotificationEvent("fcm-token-123", "지원");

            when(sqsSendOptions.queue("catchme-notification-queue")).thenReturn(sqsSendOptions);
            when(sqsSendOptions.payload(event)).thenReturn(sqsSendOptions);

            ArgumentCaptor<Consumer<SqsSendOptions<NotificationEvent>>> consumerCaptor =
                    ArgumentCaptor.forClass(Consumer.class);

            // when
            notificationProducer.sendNotificationEvent(event);

            // then
            verify(sqsTemplate).send(consumerCaptor.capture());

            Consumer<SqsSendOptions<NotificationEvent>> consumer = consumerCaptor.getValue();
            consumer.accept(sqsSendOptions);

            verify(sqsSendOptions).queue("catchme-notification-queue");
            verify(sqsSendOptions).payload(event);

            assertThat(event.getFcmToken()).isEqualTo("fcm-token-123");
            assertThat(event.getPatientName()).isEqualTo("지원");
        }
    }
}
