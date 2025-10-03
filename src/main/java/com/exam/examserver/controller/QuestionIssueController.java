// src/main/java/com/exam/examserver/controller/QuestionIssueController.java
package com.exam.examserver.controller;

import com.exam.examserver.dto.exam.QuestionIssueDTO;
import com.exam.examserver.model.user.CustomUserDetails;
import com.exam.examserver.service.QuestionIssueService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/subject/{subjectId}/questions")
@CrossOrigin("*")
public class QuestionIssueController {

    private final QuestionIssueService issueService;

    public QuestionIssueController(QuestionIssueService issueService) {
        this.issueService = issueService;
    }

    private boolean isHeadOrAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ADMIN") || a.getAuthority().equals("HEAD"));
    }

    // ---- Báo lỗi
    @PostMapping("/{questionId}/flag")
    public ResponseEntity<QuestionIssueDTO> flag(@PathVariable Long subjectId,
                                                 @PathVariable Long questionId,
                                                 @RequestBody FlagRequest req,
                                                 @AuthenticationPrincipal CustomUserDetails me) {
        QuestionIssueDTO dto = issueService.flag(subjectId, questionId, me.getId(), req.reason());
        return ResponseEntity.ok(dto);
    }

    // ---- Gỡ lỗi
    @PostMapping("/{questionId}/unflag")
    public ResponseEntity<QuestionIssueDTO> unflag(@PathVariable Long subjectId,
                                                   @PathVariable Long questionId,
                                                   @AuthenticationPrincipal CustomUserDetails me,
                                                   Authentication auth) {
        boolean privileged = isHeadOrAdmin(auth);
        QuestionIssueDTO dto = issueService.unflag(subjectId, questionId, me.getId(), privileged);
        return ResponseEntity.ok(dto);
    }

    // payload nhỏ cho flag
    public record FlagRequest(String reason) {}
}
