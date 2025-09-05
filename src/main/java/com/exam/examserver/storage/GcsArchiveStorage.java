package com.exam.examserver.storage;

import com.google.cloud.storage.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GcsArchiveStorage implements FileArchiveStorage {
    private final Storage storage;
    private final String bucket;

    public GcsArchiveStorage(Storage storage, @Value("${gcs.bucket}") String bucket) {
        this.storage = storage;
        this.bucket = bucket;
    }

    @Override
    public PutResult put(byte[] data, String contentType, String filename) {
        String safe = (filename == null || filename.isBlank()) ? "file.bin" : filename;
        String key = "archives/" + UUID.randomUUID() + "_" + safe;

        BlobInfo info = BlobInfo.newBuilder(BlobId.of(bucket, key))
                .setContentType(contentType == null ? "application/octet-stream" : contentType)
                .build();

        storage.create(info, data);
        return new PutResult(key, "");
    }

    @Override
    public void delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) return;
        storage.delete(BlobId.of(bucket, storageKey));
    }

    public PutResult putTmp(byte[] data, String contentType, String filename) {
        String safe = (filename == null || filename.isBlank()) ? "file.bin" : filename;
        String key = "tmp/" + UUID.randomUUID() + "_" + safe;

        BlobInfo info = BlobInfo.newBuilder(BlobId.of(bucket, key))
                .setContentType(contentType == null ? "application/octet-stream" : contentType)
                .build();

        storage.create(info, data);
        return new PutResult(key, "");
    }
}
