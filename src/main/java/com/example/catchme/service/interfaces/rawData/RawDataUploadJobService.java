package com.example.catchme.service.interfaces.rawData;

import com.example.catchme.model.RawDataUploadJob;
import com.example.catchme.model.User;

public interface RawDataUploadJobService {

    RawDataUploadJob createPendingJob(User user, String objectKey);

    void markS3Uploaded(Long uploadJobId);

    void markS3UploadFailed(Long uploadJobId, Exception e);

    void markDbSaveFailed(Long uploadJobId, Exception e);

    void markCompleted(Long uploadJobId);

    void markRecoveryFailed(Long uploadJobId, Exception e);
}