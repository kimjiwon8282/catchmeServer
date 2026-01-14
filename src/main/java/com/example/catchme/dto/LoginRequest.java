package com.example.catchme.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    private String email;
    private String password;
    private String fcmToken;// 추가된 필드: 앱에서 로그인할 때 현재 기기의 FCM 토큰을 같이 보내줌
}