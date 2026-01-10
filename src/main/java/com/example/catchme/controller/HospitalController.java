package com.example.catchme.controller;

import com.example.catchme.dto.HospitalResponse;
import com.example.catchme.service.interfaces.user.HospitalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/hospitals")
@RequiredArgsConstructor
public class HospitalController {

    private final HospitalService hospitalService;

    /**
     * 현재 위치 기준 근처 병원 조회
     */
    @GetMapping("/nearby")
    public List<HospitalResponse> getNearbyHospitals(
            @RequestParam double lat,
            @RequestParam double lng
    ) {
        return hospitalService.findNearbyHospitals(lat, lng);
    }
}
