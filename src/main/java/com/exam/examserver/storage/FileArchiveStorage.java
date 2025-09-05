package com.exam.examserver.storage;

public interface FileArchiveStorage {
    record PutResult(String storageKey, String publicUrl) {}
    PutResult put(byte[] data, String contentType, String filename) throws Exception;
    void delete(String storageKey) throws Exception;
}