package com.example.catchme.repository;

import com.example.catchme.model.AiPredictionResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiPredictionResultRepository extends JpaRepository<AiPredictionResult, Long> {

    // 페이징 쿼리 (Pageable에 정렬 조건이 포함되므로 OrderBy 생략 가능)
    // select * from result where user_id = ? limit ?, ?
    Page<AiPredictionResult> findAllByUserId(Long userId, Pageable pageable);
}