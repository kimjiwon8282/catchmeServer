package com.example.catchme.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 로그인 아이디 (email) */
    @Column(nullable = false, unique = true)
    private String email;

    /** 암호화된 비밀번호 */
    @Column(nullable = false)
    private String password;

    /** 사용자 표시 이름 */
    @Column(nullable = false)
    private String name;

    /** 권한 (단순화: USER / GUARDIAN / ADMIN) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /** 보호자 연동 (초기 단순화) */
    private Long guardianId;

    @Builder
    public User(String email, String password, String name, Role role) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.role = role;
    }

    /* =========================
       UserDetails 구현부
       ========================= */

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // JWT + Stateless 구조에서는 단순 권한만 있어도 충분
        return List.of(new SimpleGrantedAuthority(role.name()));
    }

    @Override
    public String getUsername() {
        // Spring Security에서의 "username"
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; // PoC에서는 항상 true
    }

    @Override
    public boolean isAccountNonLocked() {
        return true; // 잠금 기능 미구현
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // 비밀번호 만료 미구현
    }

    @Override
    public boolean isEnabled() {
        return true; // 활성화 여부 미구현
    }

    /* =========================
       도메인 메서드
       ========================= */

    public void updateName(String name) {
        this.name = name;
    }

    public void changePassword(String encode) {
        this.password = encode;
    }
}