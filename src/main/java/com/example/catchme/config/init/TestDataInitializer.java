package com.example.catchme.config.init;

import com.example.catchme.model.RawDataFile;
import com.example.catchme.model.Role;
import com.example.catchme.model.User;
import com.example.catchme.repository.RawDataFileRepository;
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
    private final RawDataFileRepository rawDataFileRepository;


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

        User user2 = User.builder()
                .email("test1@test.com")
                .password(passwordEncoder.encode("1234"))
                .name("보호자 유저")
                .role(Role.GUARDIAN)
                .build();


        userRepository.save(user);
        userRepository.save(user2);

        user.setLinkedUser(user2);
        user2.setLinkedUser(user);
        user2.updateFcmToken("test_fcm_token_12345_guardian");
        userRepository.save(user);
        userRepository.save(user2);

        RawDataFile rawDataFile = RawDataFile.create(
                user,
                "s3://test-bucket/raw-data/test-user/sample.csv"
        );
        rawDataFileRepository.save(rawDataFile);
    }
}
