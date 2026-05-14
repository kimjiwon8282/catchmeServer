package com.example.catchme.service.impl.rawData;

import com.example.catchme.dto.RawDataUploadResponse;
import com.example.catchme.dto.RawSensorDataRequest;
import com.example.catchme.exception.exceptions.IllegalCsvCreateException;
import com.example.catchme.exception.exceptions.LocalFileDeleteFailException;
import com.example.catchme.exception.exceptions.RawDataMetadataSaveFailException;
import com.example.catchme.exception.exceptions.UserNotFoundException;
import com.example.catchme.model.RawDataUploadJob;
import com.example.catchme.model.User;
import com.example.catchme.repository.UserRepository;
import com.example.catchme.service.interfaces.rawData.FileStorageService;
import com.example.catchme.service.interfaces.rawData.RawDataMetadataService;
import com.example.catchme.service.interfaces.rawData.RawDataService;
import com.example.catchme.service.interfaces.rawData.RawDataUploadJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RawDataServiceImpl implements RawDataService {

    private final FileStorageService fileStorageService;
    private final RawDataMetadataService rawDataMetadataService;
    private final RawDataUploadJobService rawDataUploadJobService;
    private final UserRepository userRepository;

    @Override
    public RawDataUploadResponse uploadRawDataAsCsv(Long userId, List<RawSensorDataRequest> requests) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        Path csvPath = null;
        String objectKey = null;
        RawDataUploadJob uploadJob = null;

        try {
            csvPath = createCsv(user, requests);

            objectKey = buildObjectKey(user);

            uploadJob = rawDataUploadJobService.createPendingJob(user, objectKey);

            try {
                fileStorageService.uploadCsv(csvPath, objectKey);
                rawDataUploadJobService.markS3Uploaded(uploadJob.getId());
            } catch (Exception e) {
                recordS3UploadFailed(uploadJob, e);
                throw e;
            }

            try {
                rawDataMetadataService.save(user, objectKey);
                rawDataUploadJobService.markCompleted(uploadJob.getId());
            } catch (Exception e) {
                recordDbSaveFailed(uploadJob, e);

                throw new RawDataMetadataSaveFailException(
                        "Raw 데이터는 S3에 저장되었지만 메타데이터 저장에 실패했습니다. 이후 복구 작업을 통해 재처리됩니다.",
                        e
                );
            }

            return new RawDataUploadResponse(objectKey);

        } finally {
            deleteLocalFile(csvPath);
        }
    }

    private void recordS3UploadFailed(RawDataUploadJob uploadJob, Exception originalException) {
        if (uploadJob == null) {
            return;
        }

        try {
            rawDataUploadJobService.markS3UploadFailed(uploadJob.getId(), originalException);
        } catch (Exception statusRecordException) {
            originalException.addSuppressed(statusRecordException);
        }
    }

    private void recordDbSaveFailed(RawDataUploadJob uploadJob, Exception originalException) {
        if (uploadJob == null) {
            return;
        }

        try {
            rawDataUploadJobService.markDbSaveFailed(uploadJob.getId(), originalException);
        } catch (Exception statusRecordException) {
            originalException.addSuppressed(statusRecordException);
        }
    }

    private Path createCsv(User user, List<RawSensorDataRequest> requests) {
        try {
            Path tempFile = Files.createTempFile(
                    "raw-data-user-" + user.getId() + "-",
                    ".csv"
            );

            try (FileWriter writer = new FileWriter(tempFile.toFile())) {
                writer.write("timestamp,p1,p2,p3,p4,acc_x,acc_y,acc_z\n");

                for (RawSensorDataRequest data : requests) {
                    writer.write(String.format(
                            "%s,%d,%d,%d,%d,%.3f,%.3f,%.3f\n",
                            data.getTimestamp(),
                            data.getPressure1(),
                            data.getPressure2(),
                            data.getPressure3(),
                            data.getPressure4(),
                            data.getAccX(),
                            data.getAccY(),
                            data.getAccZ()
                    ));
                }
            }

            return tempFile;

        } catch (Exception e) {
            throw new IllegalCsvCreateException("CSV 파일 생성에 실패했습니다.");
        }
    }

    private String buildObjectKey(User user) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);

        return "raw-data/user-" + user.getId() + "/" + now + "_" + uuid + ".csv";
    }

    private void deleteLocalFile(Path csvPath) {
        if (csvPath == null) {
            return;
        }

        try {
            Files.deleteIfExists(csvPath);
        } catch (Exception e) {
            throw new LocalFileDeleteFailException(
                    "업로드 후 로컬 CSV 삭제에 실패했습니다."
            );
        }
    }
}