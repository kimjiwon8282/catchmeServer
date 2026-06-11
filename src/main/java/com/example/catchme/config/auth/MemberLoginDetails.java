package com.example.catchme.config.auth;

import com.example.catchme.model.Role;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class MemberLoginDetails implements UserDetails {

    private final Long userId;
    private final String email;
    private final String password;
    private final Role role;
    private final boolean enabled;

    public MemberLoginDetails(Long userId, String email, String password, Role role, boolean enabled) {
        this.userId = userId;
        this.email = email;
        this.password = password;
        this.role = role;
        this.enabled = enabled;
    }

    public Long getMemberId() {
        return userId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
