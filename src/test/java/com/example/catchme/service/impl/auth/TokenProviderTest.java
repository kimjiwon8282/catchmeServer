package com.example.catchme.service.impl.auth;


import com.example.catchme.config.auth.JwtProperties;
import com.example.catchme.config.auth.TokenProvider;
import com.example.catchme.model.Role;
import com.example.catchme.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenProviderTest {

    private static final String ISSUER = "catchme"; //토큰 발급자 이름
    private static final String SECRET_KEY = "catchme-secret-key-for-hs256-test-1234567890"; //jwt서명에 사용하는 비밀키

    @Mock
    private UserDetailsService userDetailsService; //mock으로 대체

    @Nested
    @DisplayName("generateToken / validateToken")
    class GenerateAndValidate {

        @Test
        @DisplayName("유효한 사용자 정보로 생성한 토큰은 issuer, subject, id, auth 클레임을 포함하고 검증에 성공한다")
        void generateTokenAndValidateSuccess() {
            TokenProvider tokenProvider = new TokenProvider(jwtProperties(), userDetailsService);
            User user = user(1L, "user@catchme.com", "encodedPassword", "지원", Role.USER);//test용 유저 생성

            String token = tokenProvider.generateToken(user, Duration.ofHours(1));

            assertThat(tokenProvider.validateToken(token)).isTrue();
            assertThat(tokenProvider.getUserId(token)).isEqualTo(1L);

            Claims claims = parseClaims(token);
            assertThat(claims.getIssuer()).isEqualTo(ISSUER);
            assertThat(claims.getSubject()).isEqualTo("user@catchme.com");
            assertThat(claims.get("id", Long.class)).isEqualTo(1L);
            assertThat(claims.get("auth", String.class)).isEqualTo("ROLE_USER");
            assertThat(claims.getExpiration()).isAfter(new Date());
        }

        @Test
        @DisplayName("만료된 토큰은 validateToken이 false를 반환한다")
        void validateTokenReturnsFalseWhenExpired() {
            TokenProvider tokenProvider = new TokenProvider(jwtProperties(), userDetailsService);
            String expiredToken = Jwts.builder() //이미 만료된 토큰을 생성한다.
                    .setIssuer(ISSUER)
                    .setSubject("user@catchme.com")
                    .setIssuedAt(new Date(System.currentTimeMillis() - 10_000))
                    .setExpiration(new Date(System.currentTimeMillis() - 1_000))
                    .signWith(
                            Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8)),
                            io.jsonwebtoken.SignatureAlgorithm.HS256
                    )
                    .compact();

            assertThat(tokenProvider.validateToken(expiredToken)).isFalse();
        }

        @Test
        @DisplayName("형식이 잘못된 토큰은 validateToken이 false를 반환한다")
        void validateTokenReturnsFalseWhenMalformed() {
            TokenProvider tokenProvider = new TokenProvider(jwtProperties(), userDetailsService);

            assertThat(tokenProvider.validateToken("not-a-jwt-token")).isFalse();
        }
    }

    @Nested
    @DisplayName("getAuthentication")
    class GetAuthentication {

        @Test
        @DisplayName("토큰의 subject(email)로 UserDetails를 다시 조회해 Authentication을 생성한다")
        void getAuthenticationLoadsUserDetailsBySubject() {
            TokenProvider tokenProvider = new TokenProvider(jwtProperties(), userDetailsService);
            User user = user(2L, "guardian@catchme.com", "encodedPassword", "보호자", Role.GUARDIAN); //보호자 계정으로 토큰을 발급한다.
            String token = tokenProvider.generateToken(user, Duration.ofHours(1));

            when(userDetailsService.loadUserByUsername("guardian@catchme.com")).thenReturn(user); //협력객체의 반응을 고정함.

            Authentication authentication = tokenProvider.getAuthentication(token);

            assertThat(authentication.getPrincipal()).isEqualTo(user);
            assertThat(authentication.getCredentials()).isEqualTo(token);//원본 토큰 문자열이 담기는지도 확인함
            assertThat(authentication.getAuthorities())
                    .extracting("authority")
                    .containsExactly("ROLE_GUARDIAN"); //권한정보 제대로 복원되었는지 봄
            verify(userDetailsService).loadUserByUsername("guardian@catchme.com");
        }
    }

    private JwtProperties jwtProperties() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setIssuer(ISSUER);
        jwtProperties.setSecretKey(SECRET_KEY);
        return jwtProperties;
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private User user(Long id, String email, String password, String name, Role role) {
        User user = User.builder()
                .email(email)
                .password(password)
                .name(name)
                .role(role)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
