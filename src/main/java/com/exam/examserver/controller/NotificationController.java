// com.exam.examserver.controller.NotificationController.java
package com.exam.examserver.controller;

import com.exam.examserver.model.Notification;
import com.exam.examserver.model.user.CustomUserDetails;
import com.exam.examserver.service.impl.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin("*")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    private Long uid(Authentication auth) {
        return ((CustomUserDetails) auth.getPrincipal()).getId();
    }

    @GetMapping
    public Page<Notification> list(Authentication auth,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "10") int size) {
        return service.listForUser(uid(auth), page, size);
    }

    @GetMapping("/unread-count")
    public long unreadCount(Authentication auth) {
        return service.unreadCount(uid(auth));
    }

    @PostMapping("/{id}/read")
    public void markRead(Authentication auth, @PathVariable Long id) {
        service.markRead(uid(auth), id);
    }

    @PostMapping("/read-all")
    public int markAllRead(Authentication auth) {
        return service.markAllRead(uid(auth));
    }

    @DeleteMapping("/{id}")
    public void deleteOne(Authentication auth, @PathVariable Long id) {
        service.deleteOne(uid(auth), id);
    }

    @PostMapping("/{id}/click")
    public Map<String, String> click(Authentication auth, @PathVariable Long id) {
        String url = service.clickAndResolve(uid(auth), id);
        return Map.of("url", url);
    }

    // Optional: nếu muốn dùng cho email/push → 302 redirect
    @GetMapping("/{id}/go")
    public ResponseEntity<Void> go(Authentication auth, @PathVariable Long id) {
        String url = service.clickAndResolve(uid(auth), id);
        return ResponseEntity.status(302).header("Location", url).build();
    }
}
