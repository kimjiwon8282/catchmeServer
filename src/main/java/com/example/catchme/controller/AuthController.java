package com.example.catchme.controller;

import com.example.catchme.dto.LoginRequest;
import com.example.catchme.dto.LoginResponse;
import com.example.catchme.dto.SignupRequest;
import com.example.catchme.service.interfaces.auth.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 회원가입 API
     */
    @PostMapping("/signup")
    public ResponseEntity<Void> signup(
            @RequestBody SignupRequest request
    ) {
        authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();

    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @RequestBody LoginRequest request
    ) {
        return ResponseEntity.ok(authService.login(request));
    }


}
