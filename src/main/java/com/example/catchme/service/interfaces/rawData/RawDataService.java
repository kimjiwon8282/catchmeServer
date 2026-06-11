package com.example.catchme.service.interfaces.rawData;

import com.example.catchme.dto.RawDataUploadResponse;
import com.example.catchme.dto.RawSensorDataRequest;

import java.util.List;

public interface RawDataService {
    RawDataUploadResponse uploadRawDataAsCsv(Long userId, List<RawSensorDataRequest> requests);
}
