package com.example.catchme.service.impl.ai;

import com.example.catchme.dto.AiPredictionResponse;
import com.example.catchme.dto.notification.NotificationEvent;
import com.example.catchme.model.AiPredictionResult;
import com.example.catchme.model.Member;
import com.example.catchme.model.RawDataFile;
import com.example.catchme.repository.AiPredictionResultRepository;
import com.example.catchme.repository.MemberRepository;
import com.example.catchme.repository.RawDataFileRepository;
import com.example.catchme.service.impl.notification.NotificationProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PredictionResultService {

    private final RawDataFileRepository rawDataFileRepository;
    private final MemberRepository memberRepository;
    private final AiPredictionResultRepository aiPredictionResultRepository;
    private final NotificationProducer notificationProducer;

    @Transactional
    public void processResult(Long rawDataFileId, Long userId, AiPredictionResponse response) {
        RawDataFile rawDataFile = rawDataFileRepository.findById(rawDataFileId)
                .orElseThrow(() -> new IllegalStateException("파일을 찾을 수 없습니다."));

        Member member = memberRepository.findByIdWithLinkedMember(userId)
                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다."));

        rawDataFile.markAnalyzed();

        AiPredictionResult result = AiPredictionResult.builder()
                .member(member)
                .rawDataFile(rawDataFile)
                .clusterId(response.getCluster_id())
                .isRisk(response.isRisk_cluster())
                .confidence(response.getConfidence())
                .build();

        aiPredictionResultRepository.save(result);
        log.info("분석 결과 저장 완료. resultId={}", result.getId());

        if (response.isRisk_cluster()) {
            Member guardian = member.getLinkedMember();

            if (guardian != null && guardian.getFcmToken() != null) {
                NotificationEvent event = new NotificationEvent(guardian.getFcmToken(), member.getName());
                notificationProducer.sendNotificationEvent(event);
            } else {
                log.info("위험군 감지. 알림 미발송: 보호자 없음 또는 FCM 토큰 없음");
            }
        }
    }
}
