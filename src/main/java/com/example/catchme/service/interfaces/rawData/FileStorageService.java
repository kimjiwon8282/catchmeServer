package com.example.catchme.service.interfaces.rawData;

import java.nio.file.Path;

public interface FileStorageService {

    /**
     * 로컬 파일을 S3에 업로드하고 objectKey를 반환
     */
    String uploadCsv(Path filePath, String objectKey);
}