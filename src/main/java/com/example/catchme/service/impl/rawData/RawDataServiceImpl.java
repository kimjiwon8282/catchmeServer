package com.example.catchme.service.impl.rawData;

import com.example.catchme.dto.RawDataUploadResponse;
import com.example.catchme.dto.RawSensorDataRequest;
import com.example.catchme.exception.exceptions.IllegalCsvCreateException;
import com.example.catchme.exception.exceptions.LocalFileDeleteFailException;
import com.example.catchme.exception.exceptions.UserNotFoundException;
import com.example.catchme.model.RawDataFile;
import com.example.catchme.model.User;
import com.example.catchme.repository.RawDataFileRepository;
import com.example.catchme.repository.UserRepository;
import com.example.catchme.service.interfaces.rawData.FileStorageService;
import com.example.catchme.service.interfaces.rawData.RawDataMetadataService;
import com.example.catchme.service.interfaces.rawData.RawDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RawDataServiceImpl implements RawDataService {

    private final FileStorageService fileStorageService;
    private final RawDataMetadataService rawDataMetadataService;
    private final UserRepository userRepository;

    @Override
    public RawDataUploadResponse uploadRawDataAsCsv(Long userId, List<RawSensorDataRequest> requests) {
        // 1. [진입점] ID로 최신 유저 정보 조회 (영속화)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
        Path csvPath = null;
        String objectKey = null;
        try {
            /* ==========================
               1️⃣ CSV 생성 (로컬)
               ========================== */
            csvPath = createCsv(user, requests);

            /* ==========================
               2️⃣ S3 object key 생성
               ========================== */
            objectKey = buildObjectKey(user);

            /* ==========================
               3️⃣ S3 업로드 (선행)
               ========================== */
            fileStorageService.uploadCsv(csvPath, objectKey);

            // ✅ 프록시를 통한 호출 → 트랜잭션 적용
            rawDataMetadataService.save(user, objectKey);

            return new RawDataUploadResponse(objectKey);

        } catch (Exception e) {

            /* ==========================
               5️⃣ 보상 트랜잭션 (S3 롤백)
               ========================== */
            if (objectKey != null) {
                fileStorageService.deleteIfExists(objectKey);
            }

            throw e;

        } finally {

            /* ==========================
               6️⃣ 로컬 CSV 삭제
               ========================== */
            deleteLocalFile(csvPath);
        }
    }

    private Path createCsv(User user, List<RawSensorDataRequest> requests) {
        try {
            Path tempFile = Files.createTempFile(
                    "raw-data-user-" + user.getId() + "-",
                    ".csv"
            );

            try (FileWriter writer = new FileWriter(tempFile.toFile())) {
                //헤더 작성
                writer.write("timestamp,p1,p2,p3,p4,acc_x,acc_y,acc_z\n");
                //데이터 반복 작성
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
        // 예: raw-data/user-1/20251226_193000.csv
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return "raw-data/user-" + user.getId() + "/" + now + ".csv";
    }

    /* ==========================
       로컬 파일 삭제
       ========================== */
    private void deleteLocalFile(Path csvPath) {
        if (csvPath == null) return;

        try {
            Files.deleteIfExists(csvPath);
        } catch (Exception e) {
            throw new LocalFileDeleteFailException(
                    "업로드 후 로컬 CSV 삭제에 실패했습니다."
            );
        }
    }
}