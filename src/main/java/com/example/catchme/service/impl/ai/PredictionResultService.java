package com.example.catchme.service.impl.ai;

import com.example.catchme.dto.AiPredictionResponse;
import com.example.catchme.model.AiPredictionResult;
import com.example.catchme.model.RawDataFile;
import com.example.catchme.model.User;
import com.example.catchme.repository.AiPredictionResultRepository;
import com.example.catchme.repository.RawDataFileRepository;
import com.example.catchme.repository.UserRepository;
import com.example.catchme.service.interfaces.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PredictionResultService {

    private final RawDataFileRepository rawDataFileRepository;
    private final NotificationService notificationService; // ✅ 알림 서비스 주입
    private final UserRepository userRepository; // ✅ 유저 조회를 위해 주입
    private final AiPredictionResultRepository aiPredictionResultRepository; // ✅ 결과 저장을 위한 리포지토리 주입
    /**
     * 역할: 결과 저장 및 위험군 알림 발송 (트랜잭션 필수)
     */
    @Transactional
    public void processResult(Long rawDataFileId, Long userId, AiPredictionResponse response) {

        // 엔티티 조회 (영속성 컨텍스트로 불러오기)
        RawDataFile rawDataFile = rawDataFileRepository.findById(rawDataFileId)
                .orElseThrow(() -> new IllegalStateException("파일을 찾을 수 없습니다."));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다."));

        // 파일 상태 업데이트(분석됨)
        rawDataFile.markAnalyzed();

        // 3. ✨ [NEW] 분석 결과 DB 저장 (DTO -> Entity 변환)
        AiPredictionResult result = AiPredictionResult.builder()
                .user(user)               // 누구의 결과인지
                .rawDataFile(rawDataFile) // 어떤 파일의 결과인지
                .clusterId(response.getCluster_id())     // AI 결과 매핑
                .isRisk(response.isRisk_cluster())       // AI 결과 매핑
                .confidence(response.getConfidence())    // AI 결과 매핑
                .build(); // 생성자에서 analyzedAt = LocalDateTime.now() 자동 실행됨

        aiPredictionResultRepository.save(result);
        log.info("✅ 분석 결과 저장 완료 (ID: {})", result.getId());

        // 2. 위험군일 경우 알림 발송
        if (response.isRisk_cluster()) {
            User guardian = user.getLinkedUser();

            // 보호자가 있고 + 토큰도 있을 때만 발송 (없으면 그냥 조용히 넘어감)
            if (guardian != null && guardian.getFcmToken() != null) {
                notificationService.sendRiskAlert(
                        guardian.getFcmToken(),
                        user.getName()
                );
            } else {
                // (선택) 디버깅용 로그 정도는 남겨두면 좋습니다.
                log.info("ℹ️ 위험군 감지됨. (알림 미발송: 보호자 없음 or 토큰 없음)");
            }
        }
    }
}