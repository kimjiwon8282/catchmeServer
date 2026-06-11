package com.example.catchme.service.interfaces.rawData;

import com.example.catchme.model.RawDataUploadJob;
import com.example.catchme.model.Member;

public interface RawDataUploadJobService {

    RawDataUploadJob createPendingJob(Member member, String objectKey);

    void markS3Uploaded(Long uploadJobId);

    void markS3UploadFailed(Long uploadJobId, Exception e);

    void markDbSaveFailed(Long uploadJobId, Exception e);

    void markCompleted(Long uploadJobId);

    void markRecoveryFailed(Long uploadJobId, Exception e);
}
