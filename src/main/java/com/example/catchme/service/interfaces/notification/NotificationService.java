package com.example.catchme.service.interfaces.notification;

public interface NotificationService {
    /**
     * 보호자에게 위험 알림 발송
     */
    void sendRiskAlert(String fcmToken, String patientName);
}