package com.example.catchme.config.auth;

import com.example.catchme.model.Role;
import com.example.catchme.model.Member;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenProvider {

    private final JwtProperties jwtProperties;
    private final MemberAuthLookupService memberAuthLookupService;

    public String generateToken(Member member, Duration duration) {
        return generateToken(
                member.getId(),
                member.getEmail(),
                member.getRole(),
                duration
        );
    }

    public String generateToken(Long userId, String email, Role role, Duration duration) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + duration.toMillis());
        String authorities = authoritiesOf(role).stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        return Jwts.builder()
                .setHeaderParam(Header.TYPE, Header.JWT_TYPE)
                .setIssuer(jwtProperties.getIssuer())
                .setIssuedAt(now)
                .setExpiration(expiry)
                .setSubject(email)
                .claim("id", userId)
                .claim("auth", authorities)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);

            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public Authentication getAuthentication(String token) {
        Claims claims = getClaims(token);

        Number memberIdClaim = claims.get("id", Number.class);
        if (memberIdClaim == null) {
            throw new JwtException("Member id claim is missing");
        }

        Long userId = memberIdClaim.longValue();
        MemberAuthCacheDto auth = memberAuthLookupService.getMemberAuth(userId);
        if (!auth.enabled()) {
            throw new DisabledException("Disabled member: " + userId);
        }

        MemberPrincipal principal = MemberPrincipal.from(auth);

        return new UsernamePasswordAuthenticationToken(
                principal,
                token,
                principal.getAuthorities()
        );
    }

    public Long getUserId(String token) {
        return getClaims(token).get("id", Long.class);
    }

    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private List<GrantedAuthority> authoritiesOf(Role role) {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(
                jwtProperties.getSecretKey().getBytes(StandardCharsets.UTF_8)
        );
    }
}
