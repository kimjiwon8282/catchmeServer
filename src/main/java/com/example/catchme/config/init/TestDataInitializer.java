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
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class TestDataInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RawDataFileRepository rawDataFileRepository;

    @PostConstruct
    @Transactional
    public void init() {
        // 중복 초기화 방지
        if (userRepository.findByEmail("patient@test.com").isPresent()) {
            return;
        }

        // 1. 환자 유저 생성 (Role: USER) - 연동 안 된 상태
        User patient = User.builder()
                .email("patient@test.com") // 명확하게 구분하기 위해 이메일 변경
                .password(passwordEncoder.encode("1234"))
                .name("테스트 환자")
                .role(Role.USER)
                .build();

        // 2. 보호자 유저 생성 (Role: GUARDIAN) - 연동 안 된 상태
        User guardian = User.builder()
                .email("guardian@test.com") // 명확하게 구분하기 위해 이메일 변경
                .password(passwordEncoder.encode("1234"))
                .name("테스트 보호자")
                .role(Role.GUARDIAN)
                .build();

        userRepository.save(patient);
        userRepository.save(guardian);

        // 3. (옵션) 이미 연동된 커플이 필요하다면 별도로 생성 (QR 테스트 외 다른 기능 테스트용)
        // createLinkedCouple();

        // 4. 데이터 파일 생성 (환자 데이터)
        // 주의: 환자가 아직 보호자와 연동되지 않았어도 데이터는 쌓일 수 있으므로 생성해둡니다.
        RawDataFile rawDataFile = RawDataFile.create(
                patient,
                "s3://test-bucket/raw-data/test-user/sample.csv"
        );
        rawDataFileRepository.save(rawDataFile);
    }

    // 필요 시 주석을 풀어서 사용하세요 (이미 연동된 상태 테스트용)
    /*
    private void createLinkedCouple() {
        User linkedPatient = User.builder()
                .email("linked_p@test.com").password(passwordEncoder.encode("1234"))
                .name("연동된 환자").role(Role.USER).build();
        User linkedGuardian = User.builder()
                .email("linked_g@test.com").password(passwordEncoder.encode("1234"))
                .name("연동된 보호자").role(Role.GUARDIAN).build();

        userRepository.save(linkedPatient);
        userRepository.save(linkedGuardian);

        linkedPatient.setLinkedUser(linkedGuardian);
        linkedGuardian.setLinkedUser(linkedPatient);

        userRepository.save(linkedPatient);
        userRepository.save(linkedGuardian);
    }
    */
}
