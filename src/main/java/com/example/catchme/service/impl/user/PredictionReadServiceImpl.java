package com.example.catchme.service.impl.user;

import com.example.catchme.dto.PredictionHistoryResponse;
import com.example.catchme.exception.exceptions.UserNotFoundException;
import com.example.catchme.model.User;
import com.example.catchme.repository.AiPredictionResultRepository;
import com.example.catchme.repository.UserRepository;
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
    private final UserRepository userRepository;
    /**
     * [환자용] 본인의 기록 조회
     * - 이미 로그인 필터에서 탈퇴한 회원은 걸러지므로 별도 체크 불필요
     */
    @Override
    public Page<PredictionHistoryResponse> getMyHistory(Long userId, Pageable pageable) {
        return aiPredictionResultRepository.findAllByUserId(userId, pageable)
                .map(PredictionHistoryResponse::from);
    }
    /**
     * [보호자용] 연결된 환자의 기록 조회
     * - 환자가 탈퇴했는지 체크 필수!
     */
    @Override
    public Page<PredictionHistoryResponse> getPatientHistory(Long guardianId, Pageable pageable) {
        // 1. 내 트랜잭션 안으로 불러오기 (영속화)
        User guardian = userRepository.findById(guardianId)
                .orElseThrow(() -> new UserNotFoundException("보호자 정보를 찾을 수 없습니다."));
        // 1. 보호자와 연결된 환자 가져오기 (Lazy Loading 주의 -> Transactional 안이라 안전)
        User patient = guardian.getLinkedUser();

        // 2. 연결된 환자가 없는 경우
        if (patient == null) {
            throw new IllegalArgumentException("연결된 피보호자(환자)가 없습니다.");
        }

        // 3. 🔥 환자가 탈퇴했는지 체크
        if (patient.isWithdrawn()) {
            throw new UserNotFoundException("탈퇴한 회원의 데이터에는 접근할 수 없습니다.");
        }

        // 4. 조회 및 DTO 변환
        return aiPredictionResultRepository.findAllByUserId(patient.getId(), pageable)
                .map(PredictionHistoryResponse::from);
    }
}
