package com.example.catchme.service.impl.rawData;

import com.example.catchme.model.RawDataUploadJob;
import com.example.catchme.model.User;
import com.example.catchme.repository.RawDataUploadJobRepository;
import com.example.catchme.service.interfaces.rawData.RawDataUploadJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RawDataUploadJobServiceImpl implements RawDataUploadJobService {

    private static final int MAX_FAILURE_REASON_LENGTH = 1000;

    private final RawDataUploadJobRepository rawDataUploadJobRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RawDataUploadJob createPendingJob(User user, String objectKey) {
        RawDataUploadJob uploadJob = RawDataUploadJob.create(user, objectKey);
        return rawDataUploadJobRepository.save(uploadJob);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markS3Uploaded(Long uploadJobId) {
        RawDataUploadJob uploadJob = getUploadJob(uploadJobId);
        uploadJob.markS3Uploaded();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markS3UploadFailed(Long uploadJobId, Exception e) {
        RawDataUploadJob uploadJob = getUploadJob(uploadJobId);
        uploadJob.markS3UploadFailed(toFailureReason(e));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDbSaveFailed(Long uploadJobId, Exception e) {
        RawDataUploadJob uploadJob = getUploadJob(uploadJobId);
        uploadJob.markDbSaveFailed(toFailureReason(e));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(Long uploadJobId) {
        RawDataUploadJob uploadJob = getUploadJob(uploadJobId);
        uploadJob.markCompleted();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRecoveryFailed(Long uploadJobId, Exception e) {
        RawDataUploadJob uploadJob = getUploadJob(uploadJobId);
        uploadJob.markRecoveryFailed(toFailureReason(e));
    }

    private RawDataUploadJob getUploadJob(Long uploadJobId) {
        return rawDataUploadJobRepository.findById(uploadJobId)
                .orElseThrow(() -> new IllegalArgumentException("업로드 작업을 찾을 수 없습니다."));
    }

    private String toFailureReason(Exception e) {
        String message = e.getMessage();

        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }

        if (message.length() > MAX_FAILURE_REASON_LENGTH) {
            return message.substring(0, MAX_FAILURE_REASON_LENGTH);
        }

        return message;
    }
}