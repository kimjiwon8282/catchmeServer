package com.example.catchme.service.interfaces;


import com.example.catchme.dto.SignupRequest;

public interface AuthService {

    /**
     * 회원가입 처리
     */
    void signup(SignupRequest request);
}

