package com.exam.examserver.dto.importing;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ImportPreviewStore {

    /** Thông tin upload tạm (lưu ở prefix tmp/) để commit có thể "promote" */
    public static record TempUpload(String key, String originalName, String contentType, long sizeBytes) {}

    public static class Session {
        public String id;
        public Instant expiresAt;
        public List<byte[]> images = new ArrayList<>();           // ảnh thô
        public List<PreviewBlock> blocks = new ArrayList<>();     // kết quả parse

        // === NEW: thông tin upload tạm (nếu saveCopy=true ở bước preview) ===
        public TempUpload tempUpload;
    }

    private final Map<String, Session> map = new ConcurrentHashMap<>();
    private static final Duration TTL = Duration.ofMinutes(30);

    public Session create(List<byte[]> images, List<PreviewBlock> blocks) {
        Session s = new Session();
        s.id = UUID.randomUUID().toString();
        s.expiresAt = Instant.now().plus(TTL);
        s.images = images;
        s.blocks = blocks;
        map.put(s.id, s);
        return s;
    }

    public Session get(String id) {
        Session s = map.get(id);
        if (s == null) return null;
        if (Instant.now().isAfter(s.expiresAt)) { map.remove(id); return null; }
        return s;
    }

    public void remove(String id) { map.remove(id); }

    @Scheduled(fixedDelay = 300_000) // 5 phút dọn rác
    public void gc() {
        Instant now = Instant.now();
        map.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt));
    }

    // ==================== NEW APIs cho flow 2-phase ====================

    /** Gắn thông tin upload tạm (tmp/) vào session preview */
    public void attachTempUpload(String sessionId, String key, String originalName, String contentType, long sizeBytes) {
        if (sessionId == null || key == null || key.isBlank()) return;
        Session s = map.get(sessionId);
        if (s == null) return;
        s.tempUpload = new TempUpload(key, originalName, contentType, sizeBytes);
    }

    /** Lấy thông tin upload tạm (để commit promote sang archives/) */
    public TempUpload getTempUpload(String sessionId) {
        Session s = map.get(sessionId);
        if (s == null) return null;
        return s.tempUpload;
    }

    /** Xoá thông tin upload tạm khỏi session (sau khi promote xong hoặc cancel) */
    public void clearTempUpload(String sessionId) {
        Session s = map.get(sessionId);
        if (s != null) s.tempUpload = null;
    }
}
