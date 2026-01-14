package com.example.catchme.service.impl.notification;

import com.example.catchme.service.interfaces.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    @Async("taskExecutor") // ✅ 이 메서드는 이제 별도의 스레드에서 돌아갑니다! (DB 트랜잭션과 무관해짐)
    @Override
    public void sendRiskAlert(String fcmToken, String patientName) {

        // 1. 토큰 검증
        if (fcmToken == null || fcmToken.isBlank()) {
            log.warn("🚨 알림 발송 실패: FCM 토큰이 없습니다.");
            return;
        }

        // 2. (내일 구현) Firebase 발송 로직
        // 여기서 3초가 걸리든 10초가 걸리든, 메인 서버에는 아무 영향이 없음
        log.info("🚀 [비동기 발송] To: {}, Body: 환자 {}님의 위험 감지!", fcmToken, patientName);
    }
}