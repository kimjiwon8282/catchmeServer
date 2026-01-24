package com.example.catchme.config.externalApi;

import com.example.catchme.dto.AiPredictionRequest;
import com.example.catchme.dto.AiPredictionResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.PostExchange;

public interface AiPredictionClient {

    // POST 요청: base_url/predict1
    // @RequestBody: 자바 객체를 JSON Body로 변환해서 전송
    @PostExchange("/predict1")
    AiPredictionResponse requestPrediction(@RequestBody AiPredictionRequest request);
}
