package com.example.catchme.service.impl.auth;

import com.example.catchme.config.auth.TokenProvider;
import com.example.catchme.dto.LoginRequest;
import com.example.catchme.dto.LoginResponse;
import com.example.catchme.dto.SignupRequest;
import com.example.catchme.exception.exceptions.DuplicateEmailException;
import com.example.catchme.exception.exceptions.InvalidLoginException;
import com.example.catchme.model.Role;
import com.example.catchme.model.User;
import com.example.catchme.repository.UserRepository;
import com.example.catchme.service.interfaces.auth.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 1️⃣ 기본은 읽기 전용으로 설정
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;
    private static final Duration ACCESS_TOKEN_DURATION = Duration.ofHours(1);
    private final AuthenticationManager authenticationManager;
    private final CacheManager cacheManager;

    @Transactional
    @Override
    public void signup(SignupRequest request) {

        // 1️⃣ 이메일 중복 체크
        if (userRepository.existsByEmail(request.getEmail())) {
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

    // ⚠️ 중요: 데이터를 수정(FCM 토큰 저장)해야 하므로 readOnly = true를 빼야 합니다!
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
            );//loadUserByUsername 내부적 호출
        } catch (AuthenticationException e) {
            // ❗ 사용자 존재 여부 / 탈퇴 여부 / 비밀번호 오류
            // 전부 동일한 로그인 실패로 처리
            throw new InvalidLoginException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        // 여기까지 왔다는 건:
        // - UserDetailsService 호출됨
        // - withdrawn = false
        // - 비밀번호 일치
        // - isEnabled() 통과

        // 1. Redis에서 가져온 '준영속 상태'일 수 있는 유저 객체
        User user = (User) authentication.getPrincipal();

        // ✅ FCM 토큰 업데이트 (비즈니스 로직은 여전히 여기서)
        if (request.getFcmToken() != null && !request.getFcmToken().isBlank()) {
            user.updateFcmToken(request.getFcmToken());

            // ⭐️ 중요: 준영속 상태이므로 명시적으로 save()를 호출해야 DB에 반영됨
            userRepository.save(user);

            // ⭐️ 중요: DB 값이 변했으므로 기존 캐시(구형 토큰 정보)를 삭제
            evictCache(user.getEmail());
        }

        // ✅ JWT 발급
        String accessToken =
                tokenProvider.generateToken(user, ACCESS_TOKEN_DURATION);

        return new LoginResponse(accessToken, user.getRole().name());
    }
    /**
     * 캐시 삭제
     */
    private void evictCache(String email) {
        try {
            // "userCache"는 UserDetailService에서 @Cacheable(value = "userCache")로 쓴 그 이름입니다.
            Objects.requireNonNull(cacheManager.getCache("userCache")).evict(email);
            log.info("🗑️ [Cache Evict] 사용자 정보 수정으로 캐시 삭제 완료 (email: {})", email);
        } catch (Exception e) {
            log.error("⚠️ [Cache Evict Error] 캐시 삭제 중 오류 발생 (email: {})", email, e);
        }
    }
}
