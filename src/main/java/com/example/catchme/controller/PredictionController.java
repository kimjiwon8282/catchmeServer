package com.example.catchme.controller;

import com.example.catchme.dto.AiPredictionResponse;
import com.example.catchme.dto.PredictionHistoryResponse;
import com.example.catchme.model.User;
import com.example.catchme.service.interfaces.ai.PredictionService;
import com.example.catchme.service.interfaces.user.PredictionReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/predictions")
@RequiredArgsConstructor
public class PredictionController {

    private final PredictionService predictionService;
    private final PredictionReadService predictionReadService;

    @PostMapping("/latest")
    public ResponseEntity<AiPredictionResponse> predictLatest(
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(
                predictionService.requestLatestPrediction(user.getId())
        );
    }

    // 환자 본인 기록 조회
    @GetMapping("/history/me")
    public ResponseEntity<Page<PredictionHistoryResponse>> getMyHistory(
            @AuthenticationPrincipal User user,
            @PageableDefault(size = 10, sort = "analyzedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(predictionReadService.getMyHistory(user.getId(), pageable));
    }
    // 보호자가 환자 기록 조회
    @GetMapping("/history/patient")
    public ResponseEntity<Page<PredictionHistoryResponse>> getPatientHistory(
            @AuthenticationPrincipal User user,
            @PageableDefault(size = 10, sort = "analyzedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(predictionReadService.getPatientHistory(user.getId(), pageable));
    }
}