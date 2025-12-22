package com.example.catchme.service.impl;

import com.example.catchme.dto.SignupRequest;
import com.example.catchme.exception.DuplicateEmailException;
import com.example.catchme.model.Role;
import com.example.catchme.model.User;
import com.example.catchme.repository.UserRepository;
import com.example.catchme.service.interfaces.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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
                .role(request.getRole()) // 기본 권한
                .build();

        // 4️⃣ 저장
        userRepository.save(user);
    }
}
