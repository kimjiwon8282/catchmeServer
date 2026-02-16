package com.example.catchme.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AiPredictionResponse {
    // Python 서버가 반환하는 JSON 키와 이름/타입이 일치해야 함
    private int cluster_id;
    private boolean risk_cluster;
    private double confidence;
}
