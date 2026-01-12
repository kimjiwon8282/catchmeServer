package com.example.catchme.controller;

import com.example.catchme.dto.AiPredictionResponse;
import com.example.catchme.model.User;
import com.example.catchme.service.interfaces.ai.PredictionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/predictions")
@RequiredArgsConstructor
public class PredictionController {

    private final PredictionService predictionService;

    @PostMapping("/latest")
    public ResponseEntity<AiPredictionResponse> predictLatest(
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(
                predictionService.requestLatestPrediction(user)
        );
    }
}