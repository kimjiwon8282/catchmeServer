package com.example.catchme.service.impl.rawData;

import com.example.catchme.exception.exceptions.S3UploadFailException;
import com.example.catchme.service.interfaces.rawData.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import java.nio.file.Path;

@Service
@RequiredArgsConstructor
@Slf4j
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
                    .serverSideEncryption(ServerSideEncryption.AES256)
                    .build();

            s3Client.putObject(request, RequestBody.fromFile(filePath));
            return objectKey;

        } catch (Exception e) {
            throw new S3UploadFailException("S3 업로드에 실패했습니다.");
        }
    }

    @Override
    public void deleteIfExists(String objectKey) {
        try {
            s3Client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey)
                            .build()
            );
        } catch (Exception e) {
            log.info("s3파일 삭제에 실패했습니다.");
        }
    }
}
