package com.example.catchme.service.impl.user;

import com.example.catchme.dto.SurveyHistoryResponse;
import com.example.catchme.dto.SurveySubmitRequest;
import com.example.catchme.exception.exceptions.UserNotFoundException;
import com.example.catchme.model.SurveyResult;
import com.example.catchme.model.SurveyType;
import com.example.catchme.model.User;
import com.example.catchme.repository.SurveyResultRepository;
import com.example.catchme.repository.UserRepository;
import com.example.catchme.service.interfaces.user.SurveyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SurveyServiceImpl implements SurveyService {

    private final SurveyResultRepository surveyResultRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public Long submitSurvey(Long userId, SurveySubmitRequest request) {
        // 1. 유저 조회 (영속화)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        // 2. 위험군 판정 로직
        // SMCQ: 6점 이상 / K-AD8: 2점 이상
        boolean isRisk = false;
        if (request.getType() == SurveyType.SMCQ) {
            if (request.getTotalScore() >= 6) isRisk = true;
        } else if (request.getType() == SurveyType.K_AD8) {
            if (request.getTotalScore() >= 2) isRisk = true;
        }

        // 3. 엔티티 생성 및 저장
        SurveyResult surveyResult = SurveyResult.builder()
                .user(user)
                .type(request.getType())
                .totalScore(request.getTotalScore())
                .isRisk(isRisk) // 백엔드에서 계산한 결과 저장
                .answersJson(request.getAnswersJson())
                .build();

        SurveyResult savedResult = surveyResultRepository.save(surveyResult);
        log.info("📝 문진표 저장 완료 - User: {}, Type: {}, Score: {}, Risk: {}",
                user.getId(), request.getType(), request.getTotalScore(), isRisk);

        return savedResult.getId();
    }

    @Transactional(readOnly = true)
    @Override
    public Page<SurveyHistoryResponse> getMyHistory(Long userId, Pageable pageable) {
        // 본인 조회는 검증 로직이 크게 필요 없으므로 바로 조회 (쿼리 절약)
        return surveyResultRepository.findAllByUserId(userId, pageable)
                .map(SurveyHistoryResponse::from);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<SurveyHistoryResponse> getPatientHistory(Long guardianId, Pageable pageable) {
        // 1. 보호자 영속화 (Lazy Loading 준비)
        User guardian = userRepository.findById(guardianId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        // 2. 환자 조회
        User patient = guardian.getLinkedUser();
        if (patient == null) {
            throw new IllegalArgumentException("연결된 환자가 없습니다.");
        }

        // 3. 탈퇴 여부 등 보안 체크
        if (patient.isWithdrawn()) {
            throw new UserNotFoundException("탈퇴한 회원의 데이터입니다.");
        }

        // 4. 환자의 ID로 조회
        return surveyResultRepository.findAllByUserId(patient.getId(), pageable)
                .map(SurveyHistoryResponse::from);
    }


}
