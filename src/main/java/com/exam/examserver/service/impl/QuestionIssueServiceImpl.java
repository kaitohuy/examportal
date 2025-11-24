package com.exam.examserver.service.impl;

import com.exam.examserver.dto.exam.QuestionIssueDTO;
import com.exam.examserver.enums.AppArea;
import com.exam.examserver.enums.IssueStatus;
import com.exam.examserver.enums.NotificationAction;
import com.exam.examserver.enums.NotificationTargetType;
import com.exam.examserver.model.exam.Question;
import com.exam.examserver.model.exam.QuestionIssue;
import com.exam.examserver.model.exam.Subject;
import com.exam.examserver.model.user.User;
import com.exam.examserver.repo.QuestionIssueRepository;
import com.exam.examserver.repo.QuestionRepository;
import com.exam.examserver.repo.UserRepository;
import com.exam.examserver.service.QuestionIssueService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@Transactional
public class QuestionIssueServiceImpl implements QuestionIssueService {

    private final QuestionIssueRepository issueRepo;
    private final QuestionRepository questionRepo;
    private final UserRepository userRepo;
    private final NotificationService notif; // bạn đã có module notification

    public QuestionIssueServiceImpl(QuestionIssueRepository issueRepo,
                                    QuestionRepository questionRepo,
                                    UserRepository userRepo,
                                    NotificationService notif) {
        this.issueRepo = issueRepo;
        this.questionRepo = questionRepo;
        this.userRepo = userRepo;
        this.notif = notif;
    }

    @Override
    public QuestionIssueDTO flag(Long subjectId, Long questionId, Long reporterUserId, String reason) {
        if (reason == null || reason.trim().length() < 5)
            throw new IllegalArgumentException("Reason must be at least 5 characters");

        Question q = questionRepo.findById(questionId)
                .orElseThrow(() -> new EntityNotFoundException("Question not found"));

        if (!Objects.equals(q.getSubject().getId(), subjectId))
            throw new IllegalArgumentException("Subject mismatch");

        User reporter = userRepo.findById(reporterUserId)
                .orElseThrow(() -> new EntityNotFoundException("Reporter not found"));

        QuestionIssue issue = issueRepo.findByQuestionId(questionId).orElse(null);
        if (issue == null) {
            issue = new QuestionIssue();
            issue.setQuestion(q);
            issue.setReason(reason.trim());
            issue.setStatus(IssueStatus.OPEN);
            issue.setFlaggedBy(reporter);
            issue.setFlaggedAt(LocalDateTime.now());
        } else {
            // nếu đã CLEARED → mở lại; nếu đang OPEN → cập nhật lý do
            issue.setReason(reason.trim());
            issue.setStatus(IssueStatus.OPEN);
            issue.setFlaggedBy(reporter);
            issue.setFlaggedAt(LocalDateTime.now());
            issue.setClearedBy(null);
            issue.setClearedAt(null);
        }
        QuestionIssue saved = issueRepo.save(issue);

        // === Gửi thông báo tới HEAD của khoa chứa subject của câu hỏi ===
        try {
            Subject subj = q.getSubject(); // q đã load ở trên
            Long headUserId = null;
            if (subj != null && subj.getDepartment() != null && subj.getDepartment().getHeadUser() != null) {
                headUserId = subj.getDepartment().getHeadUser().getId();
            }
            if (headUserId != null) {
                String reporterName = displayName(reporter);
                String titleN = "Câu hỏi bị báo lỗi";
                String msg = reporterName + " đã báo lỗi câu hỏi #" + q.getId()
                        + " của môn " + (subj.getCode() == null ? "" : subj.getCode())
                        + ((subj.getName() == null || subj.getName().isBlank()) ? "" : " - " + subj.getName())
                        + ". Lý do: " + saved.getReason();
                // expiresAt: tuỳ bạn, để null cho thông báo thường
                notif.create(
                        headUserId,
                        "Câu hỏi bị báo lỗi",
                        reporterName + " đã báo lỗi câu hỏi #" + q.getId()
                                + " của môn " + (subj.getCode() == null ? "" : subj.getCode())
                                + ((subj.getName() == null || subj.getName().isBlank()) ? "" : " - " + subj.getName())
                                + ". Lý do: " + saved.getReason(),
                        null,
                        NotificationAction.QUESTION_FLAGGED,
                        NotificationTargetType.QUESTION,
                        q.getId(),
                        null,
                        null,
                        AppArea.ADMIN
                );

            }
        } catch (Exception ignored) {}

        return toDto(saved);
    }

    @Override
    public QuestionIssueDTO unflag(Long subjectId, Long questionId, Long actorUserId, boolean actorIsHeadOrAdmin) {
        QuestionIssue issue = issueRepo.findByQuestionId(questionId)
                .orElseThrow(() -> new EntityNotFoundException("Issue not found"));

        Question q = issue.getQuestion();
        if (!Objects.equals(q.getSubject().getId(), subjectId))
            throw new IllegalArgumentException("Subject mismatch");

        // quyền: người báo hoặc HEAD/ADMIN
        Long reporterId = issue.getFlaggedBy().getId();
        if (!actorIsHeadOrAdmin && !Objects.equals(reporterId, actorUserId))
            throw new SecurityException("Not permitted to unflag");

        User actor = userRepo.findById(actorUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        issue.setStatus(IssueStatus.CLEARED);
        issue.setClearedBy(actor);
        issue.setClearedAt(LocalDateTime.now());

        QuestionIssue saved = issueRepo.save(issue);

        // Thông báo ngược lại cho người đã báo (nếu actor là HEAD/ADMIN)
        try {
            if (actorIsHeadOrAdmin && issue.getFlaggedBy() != null) {
                String actorName = displayName(actor);
                String titleN = "Câu hỏi đã được gỡ báo lỗi";
                String msg = actorName + " đã gỡ báo lỗi cho câu hỏi #" + q.getId() + ".";
                notif.create(
                        issue.getFlaggedBy().getId(),
                        "Câu hỏi đã được gỡ báo lỗi",
                        actorName + " đã gỡ báo lỗi cho câu hỏi #" + q.getId() + ".",
                        null,
                        NotificationAction.QUESTION_UNFLAGGED,
                        NotificationTargetType.QUESTION,
                        q.getId(),
                        null,
                        null,
                        AppArea.TEACHER
                );

            }
        } catch (Exception ignored) {}

        return toDto(saved);
    }

    private QuestionIssueDTO toDto(QuestionIssue ii) {
        QuestionIssueDTO dto = new QuestionIssueDTO();
        dto.setQuestionId(ii.getQuestion().getId());
        dto.setStatus(ii.getStatus());
        dto.setReason(ii.getReason());
        dto.setFlaggedById(ii.getFlaggedBy() != null ? ii.getFlaggedBy().getId() : null);
        dto.setFlaggedByName(ii.getFlaggedBy() != null ? ii.getFlaggedBy().getUsername() : null);
        dto.setFlaggedAt(ii.getFlaggedAt());
        dto.setClearedById(ii.getClearedBy() != null ? ii.getClearedBy().getId() : null);
        dto.setClearedByName(ii.getClearedBy() != null ? ii.getClearedBy().getUsername() : null);
        dto.setClearedAt(ii.getClearedAt());
        return dto;
    }

    private String displayName(User u) {
        if (u == null) return "User";
        String f = u.getFirstName() == null ? "" : u.getFirstName().trim();
        String l = u.getLastName() == null ? "" : u.getLastName().trim();
        String full1 = (f + " " + l).trim();
        String full2 = (l + " " + f).trim();
        if (!full1.isBlank()) return full1;
        if (!full2.isBlank()) return full2;
        if (u.getUsername()!=null && !u.getUsername().isBlank()) return u.getUsername();
        if (u.getEmail()!=null && !u.getEmail().isBlank()) return u.getEmail();
        return "User #" + u.getId();
    }
}
