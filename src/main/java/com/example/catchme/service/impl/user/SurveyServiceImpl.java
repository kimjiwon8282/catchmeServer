package com.example.catchme.service.impl.user;

import com.example.catchme.dto.SurveyHistoryResponse;
import com.example.catchme.dto.SurveySubmitRequest;
import com.example.catchme.exception.exceptions.UserNotFoundException;
import com.example.catchme.model.Member;
import com.example.catchme.model.SurveyResult;
import com.example.catchme.model.SurveyType;
import com.example.catchme.repository.MemberRepository;
import com.example.catchme.repository.SurveyResultRepository;
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
    private final MemberRepository memberRepository;

    @Override
    @Transactional
    public Long submitSurvey(Long userId, SurveySubmitRequest request) {
        Member member = memberRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        boolean isRisk = false;
        if (request.getType() == SurveyType.SMCQ) {
            isRisk = request.getTotalScore() >= 6;
        } else if (request.getType() == SurveyType.K_AD8) {
            isRisk = request.getTotalScore() >= 2;
        }

        SurveyResult surveyResult = SurveyResult.builder()
                .member(member)
                .type(request.getType())
                .totalScore(request.getTotalScore())
                .isRisk(isRisk)
                .answersJson(request.getAnswersJson())
                .build();

        SurveyResult savedResult = surveyResultRepository.save(surveyResult);
        log.info("문진표 저장 완료 - Member: {}, Type: {}, Score: {}, Risk: {}",
                member.getId(), request.getType(), request.getTotalScore(), isRisk);

        return savedResult.getId();
    }

    @Transactional(readOnly = true)
    @Override
    public Page<SurveyHistoryResponse> getMyHistory(Long userId, Pageable pageable) {
        return surveyResultRepository.findAllByMemberId(userId, pageable)
                .map(SurveyHistoryResponse::from);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<SurveyHistoryResponse> getPatientHistory(Long guardianId, Pageable pageable) {
        Member guardian = memberRepository.findByIdWithLinkedMember(guardianId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        Member patient = guardian.getLinkedMember();
        if (patient == null) {
            throw new IllegalArgumentException("연결된 환자가 없습니다.");
        }

        if (patient.isWithdrawn()) {
            throw new UserNotFoundException("탈퇴한 회원의 데이터입니다.");
        }

        return surveyResultRepository.findAllByMemberId(patient.getId(), pageable)
                .map(SurveyHistoryResponse::from);
    }
}
