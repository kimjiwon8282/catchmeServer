package com.example.catchme.service.impl.auth;

import com.example.catchme.config.auth.MemberLoginDetails;
import com.example.catchme.config.auth.TokenProvider;
import com.example.catchme.dto.LoginRequest;
import com.example.catchme.dto.LoginResponse;
import com.example.catchme.dto.SignupRequest;
import com.example.catchme.exception.exceptions.DuplicateEmailException;
import com.example.catchme.exception.exceptions.InvalidLoginException;
import com.example.catchme.exception.exceptions.UserNotFoundException;
import com.example.catchme.model.Role;
import com.example.catchme.model.Member;
import com.example.catchme.repository.MemberRepository;
import com.example.catchme.service.interfaces.auth.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class AuthServiceImpl implements AuthService {

    private static final Duration ACCESS_TOKEN_DURATION = Duration.ofHours(1);

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;

    @Transactional
    @Override
    public void signup(SignupRequest request) {
        if (memberRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException("이미 존재하는 이메일입니다.");
        }

        Role role = request.getRole();
        if (role == null) {
            role = Role.USER;
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());

        Member member = Member.builder()
                .email(request.getEmail())
                .password(encodedPassword)
                .name(request.getName())
                .role(role)
                .build();

        memberRepository.save(member);
    }

    @Transactional
    @Override
    public LoginResponse login(LoginRequest request) {
        Authentication authentication;

        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );
        } catch (AuthenticationException e) {
            throw new InvalidLoginException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        MemberLoginDetails loginDetails = (MemberLoginDetails) authentication.getPrincipal();
        Member member = memberRepository.findById(loginDetails.getUserId())
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        if (request.getFcmToken() != null && !request.getFcmToken().isBlank()) {
            member.updateFcmToken(request.getFcmToken());
        }

        String accessToken = tokenProvider.generateToken(member, ACCESS_TOKEN_DURATION);

        return new LoginResponse(accessToken, member.getRole().name());
    }
}
