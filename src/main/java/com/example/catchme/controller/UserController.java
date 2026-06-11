package com.example.catchme.controller;

import com.example.catchme.dto.NameUpdateRequest;
import com.example.catchme.dto.PasswordUpdateRequest;
import com.example.catchme.config.auth.MemberPrincipal;
import com.example.catchme.service.interfaces.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PatchMapping("/name")
    public ResponseEntity<Void> updateName(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestBody NameUpdateRequest request
    ) {
        userService.updateName(principal.getMemberId(), request);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/password")
    public ResponseEntity<Void> updatePassword(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestBody PasswordUpdateRequest request
    ) {
        userService.updatePassword(principal.getMemberId(), request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        userService.deleteUser(principal.getMemberId());

        return ResponseEntity.noContent().build();
    }

}
