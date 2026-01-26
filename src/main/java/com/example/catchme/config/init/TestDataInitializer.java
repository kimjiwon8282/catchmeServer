package com.example.catchme.config.init;

import com.example.catchme.model.AiPredictionResult;
import com.example.catchme.model.RawDataFile;
import com.example.catchme.model.Role;
import com.example.catchme.model.User;
import com.example.catchme.repository.AiPredictionResultRepository;
import com.example.catchme.repository.RawDataFileRepository;
import com.example.catchme.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Random;

@Component
@RequiredArgsConstructor
public class TestDataInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RawDataFileRepository rawDataFileRepository;
    private final AiPredictionResultRepository aiPredictionResultRepository;

    @PostConstruct
    @Transactional
    public void init() {
        // 중복 초기화 방지
        if (userRepository.findByEmail("patient@test.com").isPresent()) {
            return;
        }

        // 1. [독립] 환자/보호자 생성 (연동 X)
        User patient = createUser("patient@test.com", "테스트 환자", Role.USER);
        User guardian = createUser("guardian@test.com", "테스트 보호자", Role.GUARDIAN);

        // 2. [연동] 환자/보호자 생성 및 더미 데이터 주입
        createLinkedCoupleWithDummyData();

        // 3. (선택) 독립 환자에게도 데이터 하나 정도는 넣어둠
        RawDataFile rawDataFile = RawDataFile.create(
                patient,
                "s3://test-bucket/raw-data/test-user/sample.csv"
        );
        rawDataFileRepository.save(rawDataFile);
    }

    private User createUser(String email, String name, Role role) {
        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode("1234"))
                .name(name)
                .role(role)
                .build();
        return userRepository.save(user);
    }

    private void createLinkedCoupleWithDummyData() {
        // 1. 유저 생성
        User linkedPatient = createUser("linked_p@test.com", "연동된 환자", Role.USER);
        User linkedGuardian = createUser("linked_g@test.com", "연동된 보호자", Role.GUARDIAN);

        // 2. 서로 연동 설정
        linkedPatient.setLinkedUser(linkedGuardian);
        linkedGuardian.setLinkedUser(linkedPatient);

        // 변경사항 저장 (JPA Dirty Checking으로도 되지만 명시적으로 저장)
        userRepository.save(linkedPatient);
        userRepository.save(linkedGuardian);

        // 3. 🔥 랜덤 더미 데이터 30개 생성 (페이징 테스트용)
        addRandomPredictionHistory(linkedPatient, 30);
    }

    private void addRandomPredictionHistory(User user, int count) {
        Random random = new Random();

        for (int i = 1; i <= count; i++) {
            // 3-1. RawDataFile 생성 (1:1 관계이므로 결과마다 하나씩 필요)
            RawDataFile file = RawDataFile.create(
                    user,
                    "s3://dummy-bucket/data-" + i + ".csv"
            );
            file.markAnalyzed(); // 분석 완료 처리
            rawDataFileRepository.save(file);

            // 3-2. 랜덤 값 설정
            boolean isRisk = random.nextBoolean(); // 위험군 여부 랜덤
            int clusterId = random.nextInt(3) + 1; // 1 ~ 3 랜덤
            double confidence = 0.5 + (0.5 * random.nextDouble()); // 0.5 ~ 1.0 랜덤

            // 3-3. AiPredictionResult 생성
            // *주의: 생성 시점이 루프 도는 속도만큼 미세하게 차이나서 정렬 테스트 가능
            AiPredictionResult result = AiPredictionResult.builder()
                    .user(user)
                    .rawDataFile(file)
                    .clusterId(clusterId)
                    .isRisk(isRisk)
                    .confidence(confidence)
                    .build();

            aiPredictionResultRepository.save(result);
        }
    }
}