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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)//Mockito를 JUnit5에서 쓰기 위한 설정
class AuthServiceImplTest {

    @Mock//AuthServiceImpl이 의존하는 객체들을 전부 가짜로 만듦
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenProvider tokenProvider;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache userCache;

    @Mock
    private Authentication authentication;

    @InjectMocks //가짜 객체를 넣어서 AuthServiceImpl 인스턴스를 생성한다.
    private AuthServiceImpl authService;

    @Nested
    @DisplayName("signup")
    class Signup {

        @Test
        @DisplayName("중복되지 않은 이메일이면 비밀번호를 암호화하고 회원을 저장한다")
        void signupSuccess() {
            SignupRequest request = signupRequest("new@catchme.com", "plainPassword", "신규유저", Role.GUARDIAN);
            when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
            when(passwordEncoder.encode(request.getPassword())).thenReturn("encodedPassword");

            authService.signup(request);

            verify(userRepository).existsByEmail("new@catchme.com");
            verify(passwordEncoder).encode("plainPassword");
            verify(userRepository).save(argThatUser(
                    "new@catchme.com",
                    "encodedPassword",
                    "신규유저",
                    Role.GUARDIAN
            ));
        }

        @Test
        @DisplayName("요청 role이 null이면 기본 권한 USER로 저장한다")
        void signupUsesDefaultRoleWhenRoleIsNull() {
            SignupRequest request = signupRequest("user@catchme.com", "plainPassword", "기본유저", null);
            when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
            when(passwordEncoder.encode(request.getPassword())).thenReturn("encodedPassword");

            authService.signup(request);

            verify(userRepository).save(argThatUser(
                    "user@catchme.com",
                    "encodedPassword",
                    "기본유저",
                    Role.USER
            ));
        }

        @Test
        @DisplayName("중복 이메일이면 DuplicateEmailException을 던지고 저장하지 않는다")
        void signupFailsWhenEmailAlreadyExists() {
            SignupRequest request = signupRequest("dup@catchme.com", "plainPassword", "중복유저", Role.USER);
            when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(DuplicateEmailException.class)
                    .hasMessage("이미 존재하는 이메일입니다.");

            verify(passwordEncoder, never()).encode(any());
            verify(userRepository, never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("로그인 성공 시 JWT와 역할을 반환한다")
        void loginSuccess() {
            LoginRequest request = new LoginRequest("user@catchme.com", "plainPassword", null);
            User user = user("user@catchme.com", "encodedPassword", "지원", Role.USER);

            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(authentication);//인증 성공했다 치고
            when(authentication.getPrincipal()).thenReturn(user); //인증 결과 principal은 user라고 치고
            when(tokenProvider.generateToken(eq(user), any(Duration.class))).thenReturn("access-token"); //토큰 생성 결과는 "access-token"이라 친다.

            LoginResponse response = authService.login(request);

            assertThat(response.getAccessToken()).isEqualTo("access-token");
            assertThat(response.getRole()).isEqualTo("USER");

            verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
            verify(tokenProvider).generateToken(eq(user), any(Duration.class));
            verify(userRepository, never()).save(any(User.class)); //fcm토큰이 없으니까 호출 안됨
            verify(cacheManager, never()).getCache(any()); //fcm토큰 없으니까 안불려야 함.
        }

        @Test
        @DisplayName("FCM 토큰이 있으면 사용자 정보 저장 후 userCache를 비운다")
        void loginUpdatesFcmTokenAndEvictsCache() {
            LoginRequest request = new LoginRequest("user@catchme.com", "plainPassword", "fcm-token-123");
            User user = user("user@catchme.com", "encodedPassword", "지원", Role.USER);

            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(user);
            when(cacheManager.getCache("userCache")).thenReturn(userCache);
            when(tokenProvider.generateToken(eq(user), any(Duration.class))).thenReturn("access-token");

            LoginResponse response = authService.login(request);

            assertThat(user.getFcmToken()).isEqualTo("fcm-token-123");
            assertThat(response.getAccessToken()).isEqualTo("access-token");
            assertThat(response.getRole()).isEqualTo("USER");

            verify(userRepository).save(user);
            verify(cacheManager).getCache("userCache");
            verify(userCache).evict("user@catchme.com");
            verify(tokenProvider).generateToken(eq(user), any(Duration.class));
        }

        @Test
        @DisplayName("FCM 토큰이 blank이면 저장과 캐시 삭제를 하지 않는다")
        void loginDoesNotUpdateFcmTokenWhenBlank() {
            LoginRequest request = new LoginRequest("user@catchme.com", "plainPassword", "   "); //fcm토큰이 공백이면 의미있는 토큰이 아님.
            User user = user("user@catchme.com", "encodedPassword", "지원", Role.USER);

            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(user);
            when(tokenProvider.generateToken(eq(user), any(Duration.class))).thenReturn("access-token");

            authService.login(request);

            assertThat(user.getFcmToken()).isNull();
            verify(userRepository, never()).save(any(User.class));
            verify(cacheManager, never()).getCache(any());
            verify(userCache, never()).evict(any());
        }

        @Test
        @DisplayName("인증 실패는 InvalidLoginException으로 변환한다")
        void loginFailsWhenAuthenticationFails() {
            LoginRequest request = new LoginRequest("user@catchme.com", "wrongPassword", null);
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new BadCredentialsException("bad credentials")); //스프링 시큐리티 내부에서 일부러 던지게 만듦

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(InvalidLoginException.class)
                    .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.");

            verify(tokenProvider, never()).generateToken(any(User.class), any(Duration.class));
            verify(userRepository, never()).save(any(User.class));
            verify(cacheManager, never()).getCache(any());
        }
    }

    private SignupRequest signupRequest(String email, String password, String name, Role role) {
        SignupRequest request = new SignupRequest();
        ReflectionTestUtils.setField(request, "email", email);
        ReflectionTestUtils.setField(request, "password", password);
        ReflectionTestUtils.setField(request, "name", name);
        ReflectionTestUtils.setField(request, "role", role);
        return request;
    }

    private User user(String email, String password, String name, Role role) {
        return User.builder()
                .email(email)
                .password(password)
                .name(name)
                .role(role)
                .build();
    }

    private User argThatUser(String email, String password, String name, Role role) {
        return org.mockito.ArgumentMatchers.argThat(user ->
                user.getEmail().equals(email)
                        && user.getPassword().equals(password)
                        && user.getName().equals(name)
                        && user.getRole() == role
        );
    }
}

