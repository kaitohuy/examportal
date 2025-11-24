// com.exam.examserver.model.Notification.java
package com.exam.examserver.model;

import com.exam.examserver.enums.AppArea;
import com.exam.examserver.enums.NotificationAction;
import com.exam.examserver.enums.NotificationTargetType;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "notification",
        indexes = {
                @Index(name="idx_notif_user_created", columnList = "userId,createdAt"),
                @Index(name="idx_notif_user_read",    columnList = "userId,readAt"),
                @Index(name="idx_notif_expires_at",   columnList = "expiresAt"),
                // thêm index giúp list nhanh theo unread mới
                @Index(name="idx_notif_user_isread_created", columnList = "userId,isRead,createdAt"),
                // (optional) hỗ trợ tra cứu theo resource đích
                @Index(name="idx_notif_target", columnList = "targetType,targetId")
        })
public class Notification {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(columnDefinition = "text")
    private String message;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant readAt;             // null = chưa đọc

    // GIỮ NGUYÊN để không breaking code cũ
    @Column(nullable = true)
    private boolean isRead = false;

    private Instant expiresAt;          // null = không hết hạn

    // ---- PHẦN HYBRID LINK ----
    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private NotificationAction action;          // ví dụ FILE_APPROVED

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private NotificationTargetType targetType;  // ví dụ FILE_ARCHIVE

    private Long targetId;                       // ví dụ 42

    @Column(columnDefinition = "text")
    private String payload;                      // JSON string (tab, extra...)

    @Column(length = 512)
    private String targetUrl;                    // path nội bộ, nếu muốn cấp sẵn

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private AppArea appArea;                     // ADMIN / TEACHER / STUDENT

    // --- getters/setters (nếu bạn dùng Lombok thì @Getter/@Setter là đủ) ---

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }

    // lưu ý: bạn đang gọi getIsRead() trong Service => giữ nguyên tên này
    public boolean getIsRead() { return isRead; }
    public void setIsRead(boolean read) { isRead = read; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public NotificationAction getAction() { return action; }
    public void setAction(NotificationAction action) { this.action = action; }

    public NotificationTargetType getTargetType() { return targetType; }
    public void setTargetType(NotificationTargetType targetType) { this.targetType = targetType; }

    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getTargetUrl() { return targetUrl; }
    public void setTargetUrl(String targetUrl) { this.targetUrl = targetUrl; }

    public AppArea getAppArea() { return appArea; }
    public void setAppArea(AppArea appArea) { this.appArea = appArea; }
}
