package com.example.catchme.service.interfaces.user;

import com.example.catchme.dto.NameUpdateRequest;
import com.example.catchme.dto.PasswordUpdateRequest;

public interface UserService {
    void updateName(Long userId, NameUpdateRequest request);
    void updatePassword(Long userId, PasswordUpdateRequest request);
    void deleteUser(Long userId);
}