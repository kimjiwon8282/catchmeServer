package com.example.catchme.service.impl.ai;

import com.example.catchme.dto.ai.AiPredictionRequestEvent;
import com.example.catchme.exception.exceptions.UserNotFoundException;
import com.example.catchme.model.Member;
import com.example.catchme.model.RawDataFile;
import com.example.catchme.repository.MemberRepository;
import com.example.catchme.repository.RawDataFileRepository;
import com.example.catchme.service.interfaces.ai.PredictionService;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PredictionServiceImpl implements PredictionService {

    private final RawDataFileRepository rawDataFileRepository;
    private final MemberRepository memberRepository;
    private final SqsTemplate sqsTemplate;

    @Override
    public String requestLatestPrediction(Long userId) {
        Member member = memberRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        RawDataFile rawDataFile = rawDataFileRepository
                .findTopByMemberOrderByCreatedAtDesc(member)
                .orElseThrow(() -> new IllegalStateException("측정 데이터가 없습니다."));

        if (rawDataFile.isAnalyzed()) {
            throw new IllegalStateException("이미 분석이 완료된 데이터입니다.");
        }

        AiPredictionRequestEvent event = new AiPredictionRequestEvent(
                member.getId(),
                rawDataFile.getId(),
                rawDataFile.getS3ObjectKey()
        );

        sqsTemplate.send(to -> to
                .queue("ai-prediction-request-queue")
                .payload(event)
        );

        log.info("[Producer] AI 분석 요청을 SQS에 전송했습니다. memberId={}, rawDataFileId={}",
                member.getId(), rawDataFile.getId());

        return "AI 분석 요청이 성공적으로 접수되었습니다. 완료 시 알림으로 알려드립니다.";
    }
}
