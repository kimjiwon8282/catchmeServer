package com.example.catchme.config.init;

import com.example.catchme.model.Role;
import com.example.catchme.model.User;
import com.example.catchme.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TestDataInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PostConstruct
    public void init() {
        if (userRepository.findByEmail("test@test.com").isPresent()) {
            return; // 이미 있으면 생성 안 함
        }

        User user = User.builder()
                .email("test@test.com")
                .password(passwordEncoder.encode("1234"))
                .name("테스트 유저")
                .role(Role.USER)
                .build();

        userRepository.save(user);
    }
}
