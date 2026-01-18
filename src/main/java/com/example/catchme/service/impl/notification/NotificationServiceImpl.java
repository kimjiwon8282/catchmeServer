package com.example.catchme.service.impl.notification;

import com.example.catchme.exception.exceptions.ExternalApiException;
import com.example.catchme.service.interfaces.notification.NotificationService;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    @Async("taskExecutor")
    @Override
    public void sendRiskAlert(String fcmToken, String patientName) {

        if (fcmToken == null || fcmToken.isBlank()) {
            log.warn("⚠️ FCM 토큰 없음 → 알림 생략");
            return;
        }

        try {
            Message message = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(
                            Notification.builder()
                                    .setTitle("위험 감지")
                                    .setBody(patientName + "님의 이상 징후가 감지되었습니다.")
                                    .build()
                    )
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("✅ FCM 발송 성공 messageId={}", response);

        } catch (Exception e) {
            // 🔥 개발자/운영자용 로그
            log.error("❌ FCM 전송 실패 (token={})", fcmToken, e);

            // 🔥 사용자/클라이언트용 예외
            throw new ExternalApiException("알림 전송에 실패했습니다.");
        }
    }
}