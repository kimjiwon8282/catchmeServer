package com.example.catchme.dto;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AiPredictionRequest {

    @JsonProperty("s3ObjectKey") // FastAPI 변수명에 맞게 수정
    private String s3ObjectKey;
}