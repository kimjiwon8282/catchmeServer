package com.example.catchme.service.interfaces.user;

import com.example.catchme.dto.PredictionHistoryResponse;
import com.example.catchme.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PredictionReadService {
    Page<PredictionHistoryResponse> getMyHistory(Long userId, Pageable pageable);
    Page<PredictionHistoryResponse> getPatientHistory(Long userId, Pageable pageable);

}
