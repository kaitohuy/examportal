package com.exam.examserver.storage;

import com.google.cloud.storage.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GcsObjectHelper {

    private final Storage storage;
    private final String bucket;

    public GcsObjectHelper(Storage storage, @Value("${gcs.bucket}") String bucket) {
        this.storage = storage;
        this.bucket = bucket;
    }

    public Blob putBytes(String key, String contentType, byte[] data) {
        BlobInfo info = BlobInfo.newBuilder(BlobId.of(bucket, key))
                .setContentType(contentType == null ? "application/octet-stream" : contentType)
                .build();
        return storage.create(info, data);
    }

    public Blob copyAndDelete(String fromKey, String toKey) {
        BlobId src = BlobId.of(bucket, fromKey);
        Blob srcBlob = storage.get(src);
        if (srcBlob == null) {
            throw new StorageException(404, "Source object not found: " + fromKey);
        }

        BlobInfo dstInfo = BlobInfo.newBuilder(BlobId.of(bucket, toKey))
                .setContentType(srcBlob.getContentType())
                .build();

        Storage.CopyRequest req = Storage.CopyRequest.newBuilder()
                .setSource(src)
                .setTarget(dstInfo)
                .build();

        CopyWriter cw = storage.copy(req);
        while (!cw.isDone()) cw.copyChunk();
        Blob dstBlob = cw.getResult();

        storage.delete(src);
        return dstBlob;
    }

    public boolean delete(String key) {
        return storage.delete(BlobId.of(bucket, key));
    }

    public Blob stat(String key) {
        return storage.get(BlobId.of(bucket, key));
    }
}
