package com.example.catchme.config.auth;

import com.example.catchme.model.Role;

public record MemberAuthCacheDto(
        Long memberId,
        String email,
        Role role,
        boolean enabled
) {
}
