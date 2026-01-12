package com.example.catchme.service.impl.ai;

import com.example.catchme.dto.AiPredictionRequest;
import com.example.catchme.dto.AiPredictionResponse;
import com.example.catchme.service.interfaces.ai.AiPredictionClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class AiPredictionClientImpl implements AiPredictionClient {

    private final RestTemplate restTemplate;

    @Value("${ai.fastapi.url}")
    private String fastApiUrl;

    @Override
    public AiPredictionResponse requestPrediction(String s3ObjectKey) {

        String url = fastApiUrl + "/predict1";

        AiPredictionRequest request =
                new AiPredictionRequest(s3ObjectKey);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<AiPredictionRequest> entity =
                new HttpEntity<>(request, headers);

        ResponseEntity<AiPredictionResponse> response =
                restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        entity,
                        AiPredictionResponse.class
                );

        return response.getBody();
    }
}
