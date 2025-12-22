package com.example.catchme.dto;

import lombok.Getter;

@Getter
public class PasswordUpdateRequest {
    private String currentPassword;
    private String newPassword;
}
