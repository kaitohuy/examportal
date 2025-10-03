package com.exam.examserver.service.impl;
import com.exam.examserver.model.Notification;
import com.exam.examserver.repo.NotificationRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class NotificationService {
    private final NotificationRepository repo;

    public NotificationService(NotificationRepository repo) {
        this.repo = repo;
    }

    public Page<Notification> listForUser(Long userId, int page, int size) {
        return repo.listForUser(userId, Instant.now(), PageRequest.of(page, size));
    }

    public long unreadCount(Long userId) {
        return repo.countUnreadActive(userId, Instant.now());
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        Notification n = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy"));
        if (!n.getIsRead()) {
            n.setIsRead(true);
            n.setReadAt(Instant.now()); // nếu muốn ghi lại thời điểm
            repo.save(n);
        }
    }


    @Transactional
    public int markAllRead(Long userId) {
        return repo.markAllRead(userId, Instant.now(), Instant.now());
    }

    // Factory tạo nhanh 1 thông báo
    @Transactional
    public Notification create(Long userId, String title, String message, Instant expiresAt) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setTitle(title);
        n.setMessage(message);
        n.setExpiresAt(expiresAt);
        return repo.save(n);
    }

    @Transactional
    public void deleteOne(Long userId, Long id) {
        int n = repo.deleteOne(userId, id);
        if (n == 0) throw new IllegalArgumentException("Không tìm thấy hoặc không có quyền xóa");
    }
}

