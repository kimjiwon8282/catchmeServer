package com.example.catchme.service.impl.rawData;

import com.example.catchme.model.RawDataUploadJob;
import com.example.catchme.model.RawDataUploadStatus;
import com.example.catchme.repository.RawDataFileRepository;
import com.example.catchme.repository.RawDataUploadJobRepository;
import com.example.catchme.service.interfaces.rawData.RawDataMetadataService;
import com.example.catchme.service.interfaces.rawData.RawDataUploadRecoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;

@Service
@RequiredArgsConstructor
public class RawDataUploadRecoveryServiceImpl implements RawDataUploadRecoveryService {

    private static final EnumSet<RawDataUploadStatus> RECOVERABLE_STATUSES = EnumSet.of(
            RawDataUploadStatus.DB_SAVE_FAILED,
            RawDataUploadStatus.RECOVERY_FAILED
    );

    private final RawDataUploadJobRepository rawDataUploadJobRepository;
    private final RawDataFileRepository rawDataFileRepository;
    private final RawDataMetadataService rawDataMetadataService;

    @Override
    @Transactional
    public void recover(Long uploadJobId) {
        RawDataUploadJob uploadJob = rawDataUploadJobRepository.findById(uploadJobId)
                .orElseThrow(() -> new IllegalArgumentException("업로드 작업을 찾을 수 없습니다."));

        if (!isRecoverable(uploadJob)) {
            return;
        }

        String objectKey = uploadJob.getS3ObjectKey();

        if (!rawDataFileRepository.existsByS3ObjectKey(objectKey)) {
            rawDataMetadataService.save(uploadJob.getUser(), objectKey);
        }

        uploadJob.markCompleted();
    }

    private boolean isRecoverable(RawDataUploadJob uploadJob) {
        return RECOVERABLE_STATUSES.contains(uploadJob.getStatus());
    }
}