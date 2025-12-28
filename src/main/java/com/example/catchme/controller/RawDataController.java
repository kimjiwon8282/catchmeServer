package com.example.catchme.controller;

import com.example.catchme.dto.RawDataUploadResponse;
import com.example.catchme.dto.RawSensorDataRequest;
import com.example.catchme.service.interfaces.rawData.RawDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/raw-data")
@RequiredArgsConstructor
public class RawDataController {

    private final RawDataService rawDataService;

    @PostMapping
    public ResponseEntity<RawDataUploadResponse> upload(
            @RequestBody RawSensorDataRequest request
    ) {
        return ResponseEntity.ok(rawDataService.uploadRawDataAsCsv(request));
    }
}