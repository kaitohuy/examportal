package com.exam.examserver.storage;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@Configuration
public class GcsConfig {

    /**
     * Nếu có ENV GCP_SA_JSON (chứa toàn bộ JSON service account) thì dùng nó.
     * Nếu không có, fallback về Application Default Credentials
     * (hữu ích khi dev local với GOOGLE_APPLICATION_CREDENTIALS).
     */
    @Bean
    public Storage gcsStorage(@Value("${GCP_SA_JSON:}") String gcpSaJson) throws Exception {
        GoogleCredentials creds;
        if (gcpSaJson != null && !gcpSaJson.isBlank()) {
            creds = ServiceAccountCredentials.fromStream(
                    new ByteArrayInputStream(gcpSaJson.getBytes(StandardCharsets.UTF_8))
            );
        } else {
            creds = GoogleCredentials.getApplicationDefault();
        }
        return StorageOptions.newBuilder().setCredentials(creds).build().getService();
    }
}
