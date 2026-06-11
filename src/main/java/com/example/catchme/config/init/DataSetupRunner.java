package com.example.catchme.config.init;

import com.example.catchme.model.AiPredictionResult;
import com.example.catchme.model.Member;
import com.example.catchme.model.RawDataFile;
import com.example.catchme.model.Role;
import com.example.catchme.model.SurveyResult;
import com.example.catchme.model.SurveyType;
import com.example.catchme.repository.AiPredictionResultRepository;
import com.example.catchme.repository.MemberRepository;
import com.example.catchme.repository.RawDataFileRepository;
import com.example.catchme.repository.SurveyResultRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class DataSetupRunner implements CommandLineRunner {

    private static final int TOTAL_USERS = 500_000;
    private static final int CHUNK_SIZE = 1_000;

    private final MemberRepository memberRepository;
    private final RawDataFileRepository rawDataFileRepository;
    private final AiPredictionResultRepository aiPredictionResultRepository;
    private final SurveyResultRepository surveyResultRepository;
    private final PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void run(String... args) {
        if (memberRepository.count() > 0) {
            log.info("초기 데이터가 이미 존재하여 생성을 건너뜁니다.");
            return;
        }

        log.info("대용량 더미 데이터 생성을 시작합니다. total={}", TOTAL_USERS);
        long startTime = System.currentTimeMillis();

        String encodedPassword = passwordEncoder.encode("1234");
        Random random = new Random();
        int iterations = TOTAL_USERS / CHUNK_SIZE;

        for (int i = 0; i < iterations; i++) {
            List<Member> memberBatch = new ArrayList<>();
            List<RawDataFile> fileBatch = new ArrayList<>();
            List<AiPredictionResult> aiBatch = new ArrayList<>();
            List<SurveyResult> surveyBatch = new ArrayList<>();

            for (int j = 0; j < CHUNK_SIZE; j++) {
                boolean isCouple = random.nextInt(4) < 3;

                if (isCouple) {
                    String suffix = i + "_" + j;
                    Member patient = createMember("patient_" + suffix + "@test.com", "환자_" + suffix, Role.USER, encodedPassword, random);
                    Member guardian = createMember("guardian_" + suffix + "@test.com", "보호자_" + suffix, Role.GUARDIAN, encodedPassword, random);

                    patient.setLinkedMember(guardian);
                    guardian.setLinkedMember(patient);

                    memberBatch.add(patient);
                    memberBatch.add(guardian);
                    j++;
                } else {
                    String suffix = i + "_" + j;
                    Member patient = createMember("user_" + suffix + "@test.com", "유저_" + suffix, Role.USER, encodedPassword, random);
                    memberBatch.add(patient);
                }
            }

            memberRepository.saveAll(memberBatch);

            for (Member member : memberBatch) {
                if (member.getRole() == Role.GUARDIAN) {
                    continue;
                }

                if (random.nextInt(10) == 0) {
                    continue;
                }

                int fileCount = random.nextInt(6);
                for (int k = 0; k < fileCount; k++) {
                    RawDataFile file = RawDataFile.create(member, "s3://catchme-bucket/raw/" + member.getId() + "/data_" + k + ".csv");

                    if (random.nextInt(10) < 8) {
                        file.markAnalyzed();
                        AiPredictionResult result = AiPredictionResult.builder()
                                .member(member)
                                .rawDataFile(file)
                                .clusterId(random.nextInt(3) + 1)
                                .isRisk(random.nextBoolean())
                                .confidence(0.5 + (random.nextDouble() * 0.5))
                                .build();
                        aiBatch.add(result);
                    }
                    fileBatch.add(file);
                }

                int surveyCount = random.nextInt(4);
                for (int k = 0; k < surveyCount; k++) {
                    int score = random.nextInt(30);

                    SurveyResult survey = SurveyResult.builder()
                            .member(member)
                            .type(random.nextBoolean() ? SurveyType.SMCQ : SurveyType.K_AD8)
                            .totalScore(score)
                            .isRisk(score >= 15)
                            .answersJson("{\"q1\": 1, \"q2\": 0}")
                            .build();
                    surveyBatch.add(survey);
                }
            }

            rawDataFileRepository.saveAll(fileBatch);
            aiPredictionResultRepository.saveAll(aiBatch);
            surveyResultRepository.saveAll(surveyBatch);

            entityManager.flush();
            entityManager.clear();

            log.info("{} / {} 더미 회원 데이터 생성 완료", (i + 1) * CHUNK_SIZE, TOTAL_USERS);
        }

        long endTime = System.currentTimeMillis();
        log.info("초기 데이터 생성 완료. elapsedMs={}", endTime - startTime);
    }

    private Member createMember(String email, String name, Role role, String password, Random random) {
        Member member = Member.builder()
                .email(email)
                .name(name)
                .password(password)
                .role(role)
                .build();

        if (random.nextInt(10) < 8) {
            member.updateFcmToken("fcm_token_" + UUID.randomUUID().toString().replace("-", ""));
        }

        if (random.nextInt(100) < 5) {
            member.withdraw();
        }

        return member;
    }
}
