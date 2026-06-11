package com.example.catchme.service.impl.user;

import com.example.catchme.dto.PredictionHistoryResponse;
import com.example.catchme.exception.exceptions.UserNotFoundException;
import com.example.catchme.model.Member;
import com.example.catchme.repository.AiPredictionResultRepository;
import com.example.catchme.repository.MemberRepository;
import com.example.catchme.service.interfaces.user.PredictionReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PredictionReadServiceImpl implements PredictionReadService {

    private final AiPredictionResultRepository aiPredictionResultRepository;
    private final MemberRepository memberRepository;

    @Override
    public Page<PredictionHistoryResponse> getMyHistory(Long userId, Pageable pageable) {
        return aiPredictionResultRepository.findAllByMemberId(userId, pageable)
                .map(PredictionHistoryResponse::from);
    }

    @Override
    public Page<PredictionHistoryResponse> getPatientHistory(Long guardianId, Pageable pageable) {
        Member guardian = memberRepository.findByIdWithLinkedMember(guardianId)
                .orElseThrow(() -> new UserNotFoundException("보호자 정보를 찾을 수 없습니다."));

        Member patient = guardian.getLinkedMember();
        if (patient == null) {
            throw new IllegalArgumentException("연결된 피보호자(환자)가 없습니다.");
        }

        if (patient.isWithdrawn()) {
            throw new UserNotFoundException("탈퇴한 회원의 데이터에는 접근할 수 없습니다.");
        }

        return aiPredictionResultRepository.findAllByMemberId(patient.getId(), pageable)
                .map(PredictionHistoryResponse::from);
    }
}
