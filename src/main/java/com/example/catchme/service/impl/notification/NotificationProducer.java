package com.example.catchme.service.impl.notification;

import com.example.catchme.dto.notification.NotificationEvent;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationProducer {

    private final SqsTemplate sqsTemplate; // SqsTemplate으로 교체

    public void sendNotificationEvent(NotificationEvent event) {
        log.info("📤 [Producer] SQS 큐에 알림을 던집니다! 대상: {}", event.getPatientName());

        sqsTemplate.send(to -> to
                .queue("catchme-notification-queue") // 아까 명령어로 만든 큐 이름
                .payload(event)
        );
    }
}
