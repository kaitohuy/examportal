package com.exam.examserver.storage;

import com.google.cloud.storage.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class GcsSignedUrl {
    private final Storage storage;
    private final String bucket;
    private final long ttlMin;

    public GcsSignedUrl(Storage storage,
                        @Value("${gcs.bucket}") String bucket,
                        @Value("${gcs.signed-url-ttl-min}") long ttlMin) {
        this.storage = storage;
        this.bucket = bucket;
        this.ttlMin = ttlMin;
    }

    public String sign(String storageKey, Duration ttl) {
        BlobInfo blob = BlobInfo.newBuilder(BlobId.of(bucket, storageKey)).build();
        URL url = storage.signUrl(blob, ttl.toSeconds(), TimeUnit.SECONDS,
                Storage.SignUrlOption.withV4Signature());
        return url.toString();
    }

    public String signAttachment(String key, Duration ttl, String filename, String mime) {
        var blobInfo = BlobInfo.newBuilder(bucket, key).setContentType(mime).build();
        Map<String, String> responseParams = new HashMap<>();
        responseParams.put("response-content-disposition",
                "attachment; filename=\"" + (filename == null ? "download.bin" : filename.replace("\"","")) + "\"");
        if (mime != null && !mime.isBlank()) {
            responseParams.put("response-content-type", mime);
        }
        URL url = storage.signUrl(
                blobInfo,
                ttl.toSeconds(), TimeUnit.SECONDS,
                Storage.SignUrlOption.httpMethod(HttpMethod.GET),
                Storage.SignUrlOption.withV4Signature(),
                Storage.SignUrlOption.withQueryParams(responseParams)
        );
        return url.toString();
    }

    public String signInline(String key, Duration ttl, String filename, String contentType) {
        Map<String, String> qp = Map.of(
                "response-content-disposition", "inline; filename=\"" + (filename==null?"file":filename) + "\"",
                "response-content-type", contentType == null ? "application/octet-stream" : contentType
        );
        URL url = storage.signUrl(
                BlobInfo.newBuilder(bucket, key).build(),
                ttl.toSeconds(),
                TimeUnit.SECONDS,
                Storage.SignUrlOption.httpMethod(HttpMethod.GET),
                Storage.SignUrlOption.withV4Signature(),
                Storage.SignUrlOption.withQueryParams(qp)
        );
        return url.toString();
    }
}
