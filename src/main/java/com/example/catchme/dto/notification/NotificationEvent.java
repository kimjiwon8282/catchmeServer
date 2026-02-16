package com.example.catchme.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor // 역직렬화(JSON -> 자바 객체)를 위해 기본 생성자 필수
@AllArgsConstructor
public class NotificationEvent {
    private String fcmToken;
    private String patientName;
}