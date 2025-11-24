// com.exam.examserver.service.impl.NotificationUrlResolver.java
package com.exam.examserver.service.impl;

import com.exam.examserver.enums.AppArea;
import com.exam.examserver.enums.NotificationAction;
import com.exam.examserver.enums.NotificationTargetType;
import com.exam.examserver.model.Notification;
import org.springframework.stereotype.Component;

@Component
public class NotificationUrlResolver {

    /**
     * Trả về PATH nội bộ (không kèm domain), ví dụ: "/teacher/files/42?tab=approval"
     * Nếu không đủ dữ liệu, trả về fallback như "/notifications"
     */
    public String resolve(Notification n) {
        if (n.getTargetUrl() != null && !n.getTargetUrl().isBlank()) {
            return n.getTargetUrl();
        }

        AppArea area = n.getAppArea();
        String base = switch (area != null ? area : AppArea.TEACHER) {
            case ADMIN -> "/admin";
            case TEACHER -> "/teacher";
            case HEAD -> "/head";
        };

        NotificationTargetType tt = n.getTargetType();
        Long id = n.getTargetId();

        if (tt == NotificationTargetType.EXAM_TASK && id != null) {
            // HEAD (ADMIN) vs TEACHER xem task ở khu của mình
            return base + "/tasks/" + id; // /admin/tasks/:id hoặc /teacher/tasks/:id
        }
        if (tt == NotificationTargetType.FILE_ARCHIVE && id != null) {
            return base + "/files/" + id; // tùy FE: chi tiết file/hoặc tab review
        }
        if (tt == NotificationTargetType.QUESTION && id != null) {
            return base + "/questions/" + id;
        }

        // fallback
        return base + "/notifications";
    }
}
