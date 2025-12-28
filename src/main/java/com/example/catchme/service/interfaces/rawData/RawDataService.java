package com.example.catchme.service.interfaces.rawData;

import com.example.catchme.dto.RawDataUploadResponse;
import com.example.catchme.dto.RawSensorDataRequest;

import java.nio.file.Path;

public interface RawDataService {
    RawDataUploadResponse uploadRawDataAsCsv(RawSensorDataRequest request);
}