package com.example.catchme.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RawSensorDataRequest {

    private Long userId;
    private String timestamp;

    private int pressure1;
    private int pressure2;
    private int pressure3;
    private int pressure4;

    private double accX;
    private double accY;
    private double accZ;
}