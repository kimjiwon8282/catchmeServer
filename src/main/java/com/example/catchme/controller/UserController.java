package com.example.catchme.controller;

import com.example.catchme.dto.NameUpdateRequest;
import com.example.catchme.dto.PasswordUpdateRequest;
import com.example.catchme.model.User;
import com.example.catchme.service.interfaces.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PatchMapping("/name")
    public ResponseEntity<Void> updateName(
            @AuthenticationPrincipal User user,
            @RequestBody NameUpdateRequest request
    ) {
        userService.updateName(user.getId(), request);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/password")
    public ResponseEntity<Void> updatePassword(
            @AuthenticationPrincipal User user,
            @RequestBody PasswordUpdateRequest request
    ) {
        userService.updatePassword(user.getId(), request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(
            @AuthenticationPrincipal User user
    ) {
        userService.deleteUser(user.getId());

        return ResponseEntity.noContent().build();
    }

}