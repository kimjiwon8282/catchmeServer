package com.example.catchme.controller;

import com.example.catchme.dto.SurveyHistoryResponse;
import com.example.catchme.dto.SurveySubmitRequest;
import com.example.catchme.model.User;
import com.example.catchme.service.interfaces.user.SurveyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/surveys")
@RequiredArgsConstructor
public class SurveyController {

    private final SurveyService surveyService;

    @PostMapping
    public ResponseEntity<Long> submitSurvey(
            @AuthenticationPrincipal User user,
            @RequestBody @Valid SurveySubmitRequest request
    ) {
        // 서비스에 ID만 전달하여 처리 위임
        return ResponseEntity.ok(
                surveyService.submitSurvey(user.getId(), request)
        );
    }

    // 2. 내 기록 조회 (환자용)
    @GetMapping("/history/me")
    public ResponseEntity<Page<SurveyHistoryResponse>> getMyHistory(
            @AuthenticationPrincipal User user,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(
                surveyService.getMyHistory(user.getId(), pageable)
        );
    }

    // 3. 환자 기록 조회 (보호자용)
    @GetMapping("/history/patient")
    public ResponseEntity<Page<SurveyHistoryResponse>> getPatientHistory(
            @AuthenticationPrincipal User user,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(
                surveyService.getPatientHistory(user.getId(), pageable)
        );
    }

}