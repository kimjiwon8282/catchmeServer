package com.example.catchme.service.interfaces.user;

import com.example.catchme.dto.SurveyHistoryResponse;
import com.example.catchme.dto.SurveySubmitRequest;
import com.example.catchme.model.SurveyResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SurveyService {
    /**
     * 문진표 결과 저장
     * @param userId 작성자 ID
     * @param request 문진표 데이터 (타입, 총점, 상세답변)
     * @return 저장된 문진표 ID
     */
    Long submitSurvey(Long userId, SurveySubmitRequest request);
    Page<SurveyHistoryResponse> getMyHistory(Long userId, Pageable pageable);
    Page<SurveyHistoryResponse> getPatientHistory(Long guardianId, Pageable pageable);

}