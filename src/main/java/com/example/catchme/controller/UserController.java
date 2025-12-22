package com.example.catchme.controller;

import com.example.catchme.dto.NameUpdateRequest;
import com.example.catchme.dto.PasswordUpdateRequest;
import com.example.catchme.model.User;
import com.example.catchme.service.interfaces.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PatchMapping("/name")
    public ResponseEntity<Void> updateName(
            Authentication authentication,
            @RequestBody NameUpdateRequest request
    ) {
        Long userId = ((com.example.catchme.model.User) authentication.getPrincipal()).getId();

        userService.updateName(userId, request);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/password")
    public ResponseEntity<Void> updatePassword(
            Authentication authentication,
            @RequestBody PasswordUpdateRequest request
    ) {
        User user = (User) authentication.getPrincipal();

        userService.updatePassword(user.getId(), request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(
            Authentication authentication
    ) {
        User user = (User) authentication.getPrincipal();

        userService.deleteUser(user.getId());

        return ResponseEntity.noContent().build();
    }

}