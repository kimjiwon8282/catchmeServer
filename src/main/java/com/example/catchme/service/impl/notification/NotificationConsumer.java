package com.example.catchme.service.impl.notification;

import com.example.catchme.dto.notification.NotificationEvent;
import com.example.catchme.exception.exceptions.ExternalApiException;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NotificationConsumer {
    @SqsListener("catchme-notification-queue")
    public void receiveRiskAlert(NotificationEvent event) {
        log.info("📥 [Consumer] SQS 큐에서 알림을 꺼냈습니다! 환자명: {}", event.getPatientName());

        try {
            // (하드 크래시 테스트를 또 해보시려면 아래 주석을 푸세요!)
//             log.warn("⏳ [Crash Test] 서버 즉사!");
//             Runtime.getRuntime().halt(1);

            Message message = Message.builder()
                    .setToken(event.getFcmToken())
                    .setNotification(
                            Notification.builder()
                                    .setTitle("위험 감지")
                                    .setBody(event.getPatientName() + "님의 이상 징후가 감지되었습니다.")
                                    .build()
                    )
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("✅ FCM 발송 성공 messageId={}", response);

        } catch (Exception e) {
            log.error("❌ FCM 전송 실패", e);
            throw new ExternalApiException("알림 전송 실패");
        }
    }
}