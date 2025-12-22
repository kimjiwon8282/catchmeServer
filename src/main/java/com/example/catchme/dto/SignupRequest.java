package com.example.catchme.dto;

import com.example.catchme.model.Role;
import lombok.Getter;

@Getter
public class SignupRequest {
    private String email;
    private String password;
    private String name;
    private Role role;
}
