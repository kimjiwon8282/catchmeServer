package com.example.catchme.service.interfaces.ai;

import com.example.catchme.dto.AiPredictionResponse;
import com.example.catchme.model.User;

public interface PredictionService {
        AiPredictionResponse requestLatestPrediction(User user);
}
