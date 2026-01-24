package com.example.catchme.controller;

import com.example.catchme.dto.HospitalResponse;
import com.example.catchme.dto.LocationRequest;
import com.example.catchme.service.interfaces.user.HospitalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
            @Valid @ModelAttribute LocationRequest request // DTO로 변경 및 검증 적용
    ) {
        // 서비스에는 풀어서 전달하거나 DTO째로 전달 (여기선 풀어서 전달)
        return hospitalService.findNearbyHospitals(request.getLat(), request.getLng());
    }
}
