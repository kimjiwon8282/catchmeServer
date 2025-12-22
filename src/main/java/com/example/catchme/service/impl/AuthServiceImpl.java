package com.example.catchme.service.impl;

import com.example.catchme.config.auth.TokenProvider;
import com.example.catchme.dto.LoginRequest;
import com.example.catchme.dto.LoginResponse;
import com.example.catchme.dto.SignupRequest;
import com.example.catchme.exception.exceptions.DuplicateEmailException;
import com.example.catchme.exception.exceptions.InvalidLoginException;
import com.example.catchme.model.Role;
import com.example.catchme.model.User;
import com.example.catchme.repository.UserRepository;
import com.example.catchme.service.interfaces.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor

public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;
    private static final Duration ACCESS_TOKEN_DURATION = Duration.ofHours(1);

    @Transactional
    @Override
    public void signup(SignupRequest request) {

        // 1️⃣ 이메일 중복 체크
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new DuplicateEmailException("이미 존재하는 이메일입니다.");
        }//서비스는 HTTP를 모름, 오직 도메인 의미만 던짐

        Role role = request.getRole();
        if (role == null) {
            role = Role.USER; // 기본값
        }

        // 2️⃣ 비밀번호 암호화
        String encodedPassword =
                passwordEncoder.encode(request.getPassword());

        // 3️⃣ User 엔티티 생성
        User user = User.builder()
                .email(request.getEmail())
                .password(encodedPassword)
                .name(request.getName())
                .role(role) // 기본 권한
                .build();

        // 4️⃣ 저장
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    @Override
    public LoginResponse login(LoginRequest request) {

        // 1️⃣ 이메일 기준 사용자 조회
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() ->
                        new InvalidLoginException("이메일 또는 비밀번호가 올바르지 않습니다.")
                );

        // 2️⃣ 비밀번호 검증
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidLoginException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        // 3️⃣ Access Token 생성 (⭐ TokenProvider 기준)
        String accessToken =
                tokenProvider.generateToken(user, ACCESS_TOKEN_DURATION);

        // 4️⃣ JSON 응답
        return new LoginResponse(accessToken);
    }
}
