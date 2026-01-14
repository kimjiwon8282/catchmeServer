package com.example.catchme.repository;

import com.example.catchme.model.AiPredictionResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiPredictionResultRepository extends JpaRepository<AiPredictionResult, Long> {

    // 🔍 특정 환자(User)의 결과 리스트 조회 (최신순 정렬)
    // 나중에 보호자 앱에서 "검사 이력 보기" 할 때 사용됩니다.
    List<AiPredictionResult> findAllByUserIdOrderByAnalyzedAtDesc(Long userId);
}