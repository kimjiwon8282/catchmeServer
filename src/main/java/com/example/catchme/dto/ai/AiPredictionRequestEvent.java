package com.example.catchme.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor // 역직렬화 필수
@AllArgsConstructor
public class AiPredictionRequestEvent {
    private Long userId;
    private Long rawDataFileId;
    private String s3ObjectKey;
}
