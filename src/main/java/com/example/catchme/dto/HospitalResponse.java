package com.example.catchme.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor//Redis에서 JSON을 객체로 조립할 때 반드시 필요함
@AllArgsConstructor
public class HospitalResponse {

    private String name;        // 병원명
    private String category;    // 진료과
    private String address;     // 주소
    private double latitude;
    private double longitude;
    private int distance;       // meter
} //응답 그대로 주지는 않음, 프론트가 쓰기 좋은 형태로 정제