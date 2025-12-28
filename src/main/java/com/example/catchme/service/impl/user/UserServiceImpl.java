package com.example.catchme.service.impl.user;

import com.example.catchme.dto.NameUpdateRequest;
import com.example.catchme.dto.PasswordUpdateRequest;
import com.example.catchme.exception.exceptions.InvalidPasswordException;
import com.example.catchme.exception.exceptions.UserNotFoundException;
import com.example.catchme.model.User;
import com.example.catchme.repository.UserRepository;
import com.example.catchme.service.interfaces.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void updateName(Long userId, NameUpdateRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new UserNotFoundException("사용자를 찾을 수 없습니다.")
                );

        user.updateName(request.getName());
    }

    @Override
    @Transactional
    public void updatePassword(Long userId, PasswordUpdateRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new UserNotFoundException("사용자를 찾을 수 없습니다.")
                );

        // 1️⃣ 현재 비밀번호 검증
        if (!passwordEncoder.matches(
                request.getCurrentPassword(),
                user.getPassword()
        )) {
            throw new InvalidPasswordException("비밀번호가 올바르지 않습니다.");
        }

        // 2️⃣ 새 비밀번호 암호화 & 변경
        user.changePassword(
                passwordEncoder.encode(request.getNewPassword())
        );
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new UserNotFoundException("사용자를 찾을 수 없습니다.")
                );

        userRepository.delete(user);
    }

}
