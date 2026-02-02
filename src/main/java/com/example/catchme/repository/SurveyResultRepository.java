package com.example.catchme.repository;

import com.example.catchme.model.SurveyResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;


public interface SurveyResultRepository extends JpaRepository<SurveyResult, Long> {

    Page<SurveyResult> findAllByUserId(Long userId, Pageable pageable);
}
