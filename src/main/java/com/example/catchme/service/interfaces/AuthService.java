package com.example.catchme.service.interfaces;


import com.example.catchme.dto.LoginRequest;
import com.example.catchme.dto.LoginResponse;
import com.example.catchme.dto.SignupRequest;

public interface AuthService {

    /**
     * 회원가입 처리
     */
    void signup(SignupRequest request);

    LoginResponse login(LoginRequest request);
}

