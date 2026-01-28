package com.example.catchme.repository;

import com.example.catchme.model.SurveyResult;
import com.example.catchme.model.SurveyType;
import com.example.catchme.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SurveyResultRepository extends JpaRepository<SurveyResult, Long> {

    Page<SurveyResult> findAllByUserId(Long userId, Pageable pageable);
}
