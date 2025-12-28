package com.example.catchme.config.aws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class AwsS3Config {

    @Bean
    public S3Client s3Client(
            @Value("${aws.region}") String region
    ) {
        return S3Client.builder()
                .region(Region.of(region))
                // 환경변수/프로파일/role 등 “기본 자격증명 체인” 사용
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
//✅ 이게 있으면 로컬에서는 환경변수를 읽고,
//배포(EB)에서는 IAM Role을 자동으로 읽습니다.