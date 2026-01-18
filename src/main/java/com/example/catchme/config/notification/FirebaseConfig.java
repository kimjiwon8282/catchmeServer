package com.example.catchme.config.notification;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;

@Slf4j
@Configuration
public class FirebaseConfig {

    @PostConstruct
    public void initialize() {
        try {
            if (!FirebaseApp.getApps().isEmpty()) return;

            String base64 = System.getenv("FIREBASE_CREDENTIALS_BASE64");

            if (base64 == null || base64.isBlank()) {
                log.warn("⚠️ Firebase credentials 없음 → 알림 비활성화");
                return;
            }

            byte[] decoded = Base64.getDecoder().decode(base64);
            InputStream credentialsStream = new ByteArrayInputStream(decoded);

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentialsStream))
                    .build();

            FirebaseApp.initializeApp(options);
            log.info("✅ Firebase 초기화 완료 (Base64)");

        } catch (Exception e) {
            log.error("❌ Firebase 초기화 실패", e);
        }
    }
}
