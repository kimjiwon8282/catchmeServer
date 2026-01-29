package com.example.catchme.config.init;

import com.example.catchme.model.*;
import com.example.catchme.repository.AiPredictionResultRepository;
import com.example.catchme.repository.RawDataFileRepository;
import com.example.catchme.repository.SurveyResultRepository;
import com.example.catchme.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * 대용량 더미 데이터 초기화 클래스
 * - 서버 실행 시 1회 동작 (데이터가 없을 경우에만)
 * - 목표: 10만 건 이상의 데이터를 배치(Batch) 방식으로 고속 주입
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSetupRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RawDataFileRepository rawDataFileRepository;
    private final AiPredictionResultRepository aiPredictionResultRepository;
    private final SurveyResultRepository surveyResultRepository;
    private final PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    // ==========================================
    // [설정값] 테스트 규모에 따라 조정 가능
    // ==========================================
    private static final int TOTAL_USERS = 500_000; // 목표 생성 유저 수
    private static final int CHUNK_SIZE = 1_000;    // 메모리 보호를 위한 청크(배치) 크기

    @Override
    @Transactional
    public void run(String... args) throws Exception {

        // 1. 안전장치: 데이터가 이미 있다면 실행하지 않음
        if (userRepository.count() > 0) {
            log.info("👌 데이터가 이미 존재하여 초기화를 건너뜁니다.");
            return;
        }

        log.info("🚀 대용량 더미 데이터 생성을 시작합니다... (목표: {}명)", TOTAL_USERS);
        long startTime = System.currentTimeMillis();

        // 2. 비밀번호 암호화는 '딱 한 번'만 수행 후 재사용 (성능 최적화 핵심)
        String encodedPassword = passwordEncoder.encode("1234");
        Random random = new Random();

        // 3. 청크 단위로 루프 실행 (예: 100,000 / 1,000 = 100번 반복)
        int iterations = TOTAL_USERS / CHUNK_SIZE;

        for (int i = 0; i < iterations; i++) {
            List<User> userBatch = new ArrayList<>();
            List<RawDataFile> fileBatch = new ArrayList<>();
            List<AiPredictionResult> aiBatch = new ArrayList<>();
            List<SurveyResult> surveyBatch = new ArrayList<>();

            // -------------------------------------------------
            // [Step 1] 유저 생성 루프 (한 번에 1,000명씩)
            // -------------------------------------------------
            for (int j = 0; j < CHUNK_SIZE; j++) {

                // [확률 로직] 환자 8명 중 6명(75%)은 보호자와 연동됨 (0, 1, 2 = true / 3 = false)
                boolean isCouple = random.nextInt(4) < 3;

                if (isCouple) {
                    // 1-1. 환자 & 보호자 쌍(Couple) 생성
                    String suffix = i + "_" + j;
                    User patient = createUser("patient_" + suffix + "@test.com", "환자_" + suffix, Role.USER, encodedPassword, random);
                    User guardian = createUser("guardian_" + suffix + "@test.com", "보호자_" + suffix, Role.GUARDIAN, encodedPassword, random);

                    // 서로 연동
                    patient.setLinkedUser(guardian);
                    guardian.setLinkedUser(patient);

                    userBatch.add(patient);
                    userBatch.add(guardian);
                    j++; // 두 명을 만들었으므로 인덱스 추가 증가
                } else {
                    // 1-2. 1인 환자 생성 (보호자 없음)
                    String suffix = i + "_" + j;
                    User patient = createUser("user_" + suffix + "@test.com", "유저_" + suffix, Role.USER, encodedPassword, random);
                    userBatch.add(patient);
                }
            }

            // DB에 유저 먼저 저장 (ID 생성을 위해 필수)
            userRepository.saveAll(userBatch);

            // -------------------------------------------------
            // [Step 2] 생성된 유저들에게 데이터(파일, 설문 등) 부여
            // -------------------------------------------------
            for (User user : userBatch) {
                // [규칙] 보호자는 검사 데이터를 가질 수 없음
                if (user.getRole() == Role.GUARDIAN) continue;

                // [확률] 10%는 가입만 하고 아무것도 안 한 '깡통 유저'
                if (random.nextInt(10) == 0) continue;

                // A. RawDataFile & AI 결과 생성 (0~5개 랜덤)
                int fileCount = random.nextInt(6);
                for (int k = 0; k < fileCount; k++) {
                    RawDataFile file = RawDataFile.create(user, "s3://catchme-bucket/raw/" + user.getId() + "/data_" + k + ".csv");

                    // 80% 확률로 분석 완료 처리
                    if (random.nextInt(10) < 8) {
                        file.markAnalyzed();
                        // AI 결과 생성 (파일과 1:1 매핑)
                        AiPredictionResult result = AiPredictionResult.builder()
                                .user(user)
                                .rawDataFile(file)
                                .clusterId(random.nextInt(3) + 1) // 1~3 유형
                                .isRisk(random.nextBoolean())     // 위험군 여부
                                .confidence(0.5 + (random.nextDouble() * 0.5)) // 0.5 ~ 1.0 신뢰도
                                .build();
                        aiBatch.add(result);
                    }
                    fileBatch.add(file);
                }

                // B. 설문 결과 생성 (0~3개 랜덤)
                int surveyCount = random.nextInt(4);
                for (int k = 0; k < surveyCount; k++) {
                    int score = random.nextInt(30); // 0~29점

                    SurveyResult survey = SurveyResult.builder()
                            .user(user)
                            .type(random.nextBoolean() ? SurveyType.SMCQ : SurveyType.K_AD8)
                            .totalScore(score)
                            .isRisk(score >= 15) // 15점 이상 위험군 가정
                            .answersJson("{\"q1\": 1, \"q2\": 0}") // 더미 JSON
                            .build();
                    surveyBatch.add(survey);
                }
            }

            // 종속 데이터 일괄 저장 (Batch Insert)
            rawDataFileRepository.saveAll(fileBatch);
            aiPredictionResultRepository.saveAll(aiBatch);
            surveyResultRepository.saveAll(surveyBatch);

            // -------------------------------------------------
            // [Step 3] 메모리 정리 (핵심 성능 포인트)
            // -------------------------------------------------
            // 쌓인 1차 캐시를 비워주지 않으면 OOM(OutOfMemory) 발생 위험
            entityManager.flush();
            entityManager.clear();

            log.info("✅ {} / {} 명 데이터 생성 완료...", (i + 1) * CHUNK_SIZE, TOTAL_USERS);
        }

        long endTime = System.currentTimeMillis();
        log.info("🎉 모든 초기화 완료! 소요 시간: {}ms", (endTime - startTime));
    }

    // 유저 생성 헬퍼 메서드 (FCM 토큰 및 탈퇴 로직 포함)
    private User createUser(String email, String name, Role role, String password, Random random) {
        // 1. 기본 객체 생성
        User user = User.builder()
                .email(email)
                .name(name)
                .password(password) // 미리 암호화된 비밀번호 사용
                .role(role)
                .build();

        // 2. [FCM 토큰] 활동 유저의 80%는 앱을 설치함 (토큰 보유)
        // 탈퇴 처리 전에 토큰을 먼저 넣어야 함 (withdraw()가 토큰을 날리기 때문)
        if (random.nextInt(10) < 8) {
            // 랜덤 UUID를 사용하여 실제 토큰처럼 길게 생성
            user.updateFcmToken("fcm_token_" + UUID.randomUUID().toString().replace("-", ""));
        }

        // 3. [회원 탈퇴] 전체의 5%는 탈퇴한 회원
        if (random.nextInt(100) < 5) {
            user.withdraw(); // withdrawn=true, withdrawnAt=now, fcmToken=null 처리됨
        }

        return user;
    }
}