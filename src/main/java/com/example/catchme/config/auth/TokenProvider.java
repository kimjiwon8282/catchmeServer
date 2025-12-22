package com.example.catchme.config.auth;

import com.example.catchme.model.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Duration;
import java.util.Date;

/**
 * JWT 토큰의 생성 / 검증 / 인증 객체 변환을 담당하는 클래스
 *
 * ✔ Access Token only 구조
 * ✔ Refresh Token 사용하지 않음 (의도적 단순화)
 * ✔ Stateless 인증
 * ✔ UserDetailsService를 통한 서버 기준 사용자 검증
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenProvider {

    /**
     * application.yml(jwt.*) 에서 주입받는 설정 값
     * - issuer      : 토큰 발급자
     * - secretKey   : 서명에 사용할 비밀키
     */
    private final JwtProperties jwtProperties;

    /**
     * JWT 토큰에서 추출한 사용자 식별값(email)을
     * 실제 User 엔티티(UserDetails)로 변환하기 위해 사용
     *
     * → JWT를 전적으로 신뢰하지 않고
     * → 항상 "서버 기준" 사용자 상태를 조회하기 위함
     */
    private final UserDetailsService userDetailsService;

    /* =========================================================
       1. JWT 토큰 생성
       ========================================================= */

    /**
     * 사용자 정보와 토큰 유효기간을 받아 Access Token을 생성
     *
     * @param user     인증된 사용자 엔티티
     * @param duration 토큰 유효기간 (예: 1시간)
     * @return JWT 문자열
     */
    public String generateToken(User user, Duration duration) {
        Date now = new Date(); // 토큰 발급 시각
        Date expiry = new Date(now.getTime() + duration.toMillis()); // 만료 시각

        return Jwts.builder()
                // [Header] JWT 타입 명시
                .setHeaderParam(Header.TYPE, Header.JWT_TYPE)

                // [Payload - 표준 클레임]
                .setIssuer(jwtProperties.getIssuer()) // iss: 토큰 발급자
                .setIssuedAt(now)                     // iat: 발급 시각
                .setExpiration(expiry)                // exp: 만료 시각
                .setSubject(user.getEmail())           // sub: 사용자 식별자 (email)

                // [Payload - 커스텀 클레임]
                // → DB 조회 시 사용할 수 있도록 사용자 id 포함
                .claim("id", user.getId())

                // [Signature]
                // 문자열 secretKey를 그대로 쓰지 않고,
                // 암호학적으로 안전한 Key 객체로 변환하여 사용
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)

                .compact();
    }

    /* =========================================================
       2. JWT 토큰 검증
       ========================================================= */

    /**
     * JWT 토큰의 서명, 만료 여부를 검증
     *
     * @param token JWT 문자열
     * @return 유효하면 true, 아니면 false
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey()) // 서명 키 설정
                    .build()
                    .parseClaimsJws(token);         // 파싱 시 모든 검증 수행

            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // 서명 불일치, 만료, 형식 오류 등
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /* =========================================================
       3. JWT → Spring Security Authentication 변환
       ========================================================= */

    /**
     * JWT 토큰을 기반으로 Spring Security Authentication 객체 생성
     *
     * 핵심 포인트:
     * ✔ 토큰에서 email(sub)만 추출
     * ✔ UserDetailsService를 통해 DB 기준 사용자 재조회
     * ✔ 토큰을 "신분증"처럼만 사용
     *
     * @param token JWT 문자열
     * @return Authentication 객체
     */
    public Authentication getAuthentication(String token) {
        Claims claims = getClaims(token);

        // JWT에 담긴 email(sub)을 기준으로
        // 실제 UserDetails를 DB에서 다시 조회
        UserDetails userDetails =
                userDetailsService.loadUserByUsername(claims.getSubject());

        // Spring Security가 인식하는 인증 객체 생성
        return new UsernamePasswordAuthenticationToken(
                userDetails,               // principal (UserDetails)
                token,                     // credentials (JWT)
                userDetails.getAuthorities() // 권한 정보
        );
    }

    /* =========================================================
       4. 부가 유틸 메서드
       ========================================================= */

    /**
     * JWT 토큰에서 사용자 ID(id 클레임)만 추출
     * → 컨트롤러/서비스 계층에서 빠르게 사용자 식별 시 사용 가능
     */
    public Long getUserId(String token) {
        return getClaims(token).get("id", Long.class);
    }

    /**
     * JWT 토큰의 Claims(Payload)를 파싱하여 반환
     * 내부적으로 서명 검증 및 만료 검증이 함께 수행됨
     */
    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 문자열 기반 secretKey를
     * HS256 알고리즘에 맞는 Key 객체로 변환
     *
     * 이유:
     * - 키 길이(256bit) 자동 검증
     * - JJWT 0.11+ 권장 방식
     * - 암호학적으로 명확한 타입 사용
     */
    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(
                jwtProperties.getSecretKey().getBytes(StandardCharsets.UTF_8)
        );
    }
}
