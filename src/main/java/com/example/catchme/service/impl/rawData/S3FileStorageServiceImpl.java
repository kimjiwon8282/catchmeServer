package com.example.catchme.service.impl.rawData;

import com.example.catchme.exception.exceptions.S3UploadFailException;
import com.example.catchme.service.interfaces.rawData.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class S3FileStorageServiceImpl implements FileStorageService {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Override
    public String uploadCsv(Path filePath, String objectKey) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType("text/csv")
                    .build();

            s3Client.putObject(request, RequestBody.fromFile(filePath));
            return objectKey;

        } catch (Exception e) {
            throw new S3UploadFailException("S3 업로드에 실패했습니다.");
        }
    }
}
