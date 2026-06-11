package com.example.catchme.controller;

import com.example.catchme.config.auth.MemberPrincipal;
import com.example.catchme.dto.SurveyHistoryResponse;
import com.example.catchme.dto.SurveySubmitRequest;
import com.example.catchme.service.interfaces.user.SurveyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/surveys")
@RequiredArgsConstructor
public class SurveyController {

    private final SurveyService surveyService;

    @PostMapping
    public ResponseEntity<Long> submitSurvey(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestBody @Valid SurveySubmitRequest request
    ) {
        return ResponseEntity.ok(
                surveyService.submitSurvey(principal.getMemberId(), request)
        );
    }

    @GetMapping("/history/me")
    public ResponseEntity<Page<SurveyHistoryResponse>> getMyHistory(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(
                surveyService.getMyHistory(principal.getMemberId(), pageable)
        );
    }

    @GetMapping("/history/patient")
    public ResponseEntity<Page<SurveyHistoryResponse>> getPatientHistory(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(
                surveyService.getPatientHistory(principal.getMemberId(), pageable)
        );
    }
}
