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
import com.example.catchme.service.interfaces.rawData.RawDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Transactional
public class RawDataServiceImpl implements RawDataService {

    private final FileStorageService fileStorageService;
    private final RawDataFileRepository rawDataFileRepository;

    @Override
    public RawDataUploadResponse uploadRawDataAsCsv(User user, RawSensorDataRequest request) {

        // 2️⃣ CSV 생성
        Path csvPath = createCsv(user, request);

        // 3️⃣ S3 object key 생성
        String objectKey = buildObjectKey(user);

        // 4️⃣ S3 업로드
        String savedKey = fileStorageService.uploadCsv(csvPath, objectKey);

        // 5️⃣ 메타데이터 DB 저장
        RawDataFile rawDataFile = RawDataFile.create(user, savedKey);
        rawDataFileRepository.save(rawDataFile);

        // 6️⃣ 로컬 CSV 삭제
        try {
            Files.deleteIfExists(csvPath);
        } catch (Exception e) {
            throw new LocalFileDeleteFailException("업로드 후 로컬 CSV 삭제에 실패했습니다.");
        }

        return new RawDataUploadResponse(savedKey);
    }

    private Path createCsv(User user, RawSensorDataRequest request) {
        try {
            Path tempFile = Files.createTempFile(
                    "raw-data-user-" + user.getId() + "-",
                    ".csv"
            );

            try (FileWriter writer = new FileWriter(tempFile.toFile())) {
                writer.write("timestamp,p1,p2,p3,p4,acc_x,acc_y,acc_z\n");
                writer.write(String.format(
                        "%s,%d,%d,%d,%d,%.3f,%.3f,%.3f\n",
                        request.getTimestamp(),
                        request.getPressure1(),
                        request.getPressure2(),
                        request.getPressure3(),
                        request.getPressure4(),
                        request.getAccX(),
                        request.getAccY(),
                        request.getAccZ()
                ));
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
}