package com.example.catchme.config.auth;

import com.example.catchme.model.Role;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

@Getter
public class MemberPrincipal {

    private final Long memberId;
    private final String email;
    private final Role role;
    private final boolean enabled;

    public MemberPrincipal(Long memberId, String email, Role role, boolean enabled) {
        this.memberId = memberId;
        this.email = email;
        this.role = role;
        this.enabled = enabled;
    }

    public static MemberPrincipal from(MemberAuthCacheDto dto) {
        return new MemberPrincipal(
                dto.memberId(),
                dto.email(),
                dto.role(),
                dto.enabled()
        );
    }

    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
