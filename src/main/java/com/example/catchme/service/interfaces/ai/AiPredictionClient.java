package com.example.catchme.service.interfaces.ai;

import com.example.catchme.dto.AiPredictionResponse;

public interface AiPredictionClient {

    AiPredictionResponse requestPrediction(String s3ObjectKey);
}