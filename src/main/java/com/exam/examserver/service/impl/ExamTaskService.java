package com.exam.examserver.service.impl;

import com.exam.examserver.dto.tasks.ExamTaskCreateDTO;
import com.exam.examserver.enums.ExamTaskStatus;
import com.exam.examserver.model.exam.ExamTask;
import com.exam.examserver.model.exam.Subject;
import com.exam.examserver.repo.*;
import com.exam.examserver.service.QuestionMetaService;
import com.exam.examserver.service.QuestionService;
import com.exam.examserver.service.import_export.FileArchiveService;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import com.exam.examserver.enums.AppArea;
import com.exam.examserver.enums.NotificationAction;
import com.exam.examserver.enums.NotificationTargetType;

@Service
public class ExamTaskService {
    private final ExamTaskRepository taskRepo;
    private final SubjectRepository subjectRepo;
    private final UserRepository userRepo;
    private final DepartmentRepository deptRepo;
    private final NotificationService notif;
    private final FileArchiveService fileArchiveService;
    private final QuestionMetaService questionMetaService;

    public ExamTaskService(ExamTaskRepository taskRepo,
                           SubjectRepository subjectRepo,
                           UserRepository userRepo,
                           DepartmentRepository deptRepo,
                           NotificationService notif, FileArchiveService fileArchiveService, QuestionMetaService questionMetaService) {
        this.taskRepo = taskRepo;
        this.subjectRepo = subjectRepo;
        this.userRepo = userRepo;
        this.deptRepo = deptRepo;
        this.notif = notif;
        this.fileArchiveService = fileArchiveService;
        this.questionMetaService = questionMetaService;
    }

    private String displayName(Long userId) {
        return userRepo.findById(userId).map(u -> {
            String f = Optional.ofNullable(u.getFirstName()).orElse("").trim();
            String l = Optional.ofNullable(u.getLastName()).orElse("").trim();
            String full1 = (f + " " + l).trim();
            String full2 = (l + " " + f).trim();
            if (!full1.isBlank()) return full1;
            if (!full2.isBlank()) return full2;
            if (u.getUsername()!=null && !u.getUsername().isBlank()) return u.getUsername();
            if (u.getEmail()!=null && !u.getEmail().isBlank()) return u.getEmail();
            return "User #" + u.getId();
        }).orElse("User #" + userId);
    }

    @Transactional
    public ExamTask createByHead(Long headUserId, Long subjectId, Long assignedToId,
                                 String title, String instructions, String structureJson, Instant dueAt) {
        Subject subj = subjectRepo.findById(subjectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Môn không tồn tại"));
        Long deptId = subj.getDepartment().getId();

        // Optional: kiểm tra headUserId có phải head của khoa subj không
        deptRepo.findByHeadUser_Id(headUserId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không phải HEAD của khoa này"));

        ExamTask t = new ExamTask();
        t.setSubjectId(subjectId);
        t.setHeadDepartmentId(deptId);
        t.setAssignedToId(assignedToId);
        t.setCreatedByHeadId(headUserId);
        t.setTitle(title);
        t.setInstructions((instructions == null || instructions.isBlank()) ? null : instructions);
        t.setStructureJson(structureJson);
        t.setDueAt(dueAt);
        t.setStatus(ExamTaskStatus.ASSIGNED);
        taskRepo.save(t);

        // Notify teacher
        String headName = displayName(headUserId);
        String subjName = subj.getCode() + (subj.getName() == null ? "" : " - " + subj.getName());
        String titleN = "Nhiệm vụ ra đề mới";
        String msg = headName + " giao nhiệm vụ: \"" + title + "\" cho môn " + subjName + ".";
        notif.create(
                assignedToId,
                "Nhiệm vụ ra đề mới",
                headName + " giao nhiệm vụ: \"" + title + "\" cho môn " + subjName + ".",
                null,
                NotificationAction.TASK_ASSIGNED,
                NotificationTargetType.EXAM_TASK,
                t.getId(),
                null,              // payload JSON (optional)
                null,              // targetUrl (optional)
                AppArea.TEACHER
        );

        return t;
    }

    @Transactional
    public ExamTask updateStatusByTeacher(Long teacherId, Long taskId, ExamTaskStatus next) {
        ExamTask t = taskRepo.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!t.getAssignedToId().equals(teacherId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không phải nhiệm vụ của bạn");
        }
        if (t.getStatus() == ExamTaskStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nhiệm vụ đã bị huỷ");
        }

        if (next == ExamTaskStatus.IN_PROGRESS) {
            t.setStatus(ExamTaskStatus.IN_PROGRESS);
            t = taskRepo.save(t);

            // Notify HEAD khi bắt đầu
            String teacherName = displayName(teacherId);
            notif.create(
                    t.getCreatedByHeadId(),
                    "Giáo viên bắt đầu nhiệm vụ",
                    teacherName + " đã bắt đầu: \"" + t.getTitle() + "\".",
                    null,
                    NotificationAction.TASK_STARTED,
                    NotificationTargetType.EXAM_TASK,
                    t.getId(),
                    null,
                    null,
                    AppArea.ADMIN
            );
            return t;
        }

        // chặn các trạng thái khác (đặc biệt DONE)
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không hợp lệ. Để nộp bài, dùng endpoint /submit.");
    }

    @Transactional
    public ExamTask cancelByHead(Long headUserId, Long taskId) {
        ExamTask t = taskRepo.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        // check quyền HEAD như code hiện có
        t.setStatus(ExamTaskStatus.CANCELLED);
        t = taskRepo.save(t);

        // NEW: thông báo cho giáo viên
        String headName = displayName(headUserId);
        String titleN = "Nhiệm vụ đã bị huỷ";
        String msg = headName + " đã huỷ nhiệm vụ: \"" + t.getTitle() + "\".";
        notif.create(
                t.getAssignedToId(),
                "Nhiệm vụ đã bị huỷ",
                headName + " đã huỷ nhiệm vụ: \"" + t.getTitle() + "\".",
                null,
                NotificationAction.TASK_CANCELLED,
                NotificationTargetType.EXAM_TASK,
                t.getId(),
                null,
                null,
                AppArea.TEACHER
        );

        return t;
    }

    @Transactional
    public void deleteByHead(Long headUserId, Long taskId) {
        ExamTask t = taskRepo.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // Giữ nguyên kiểm tra quyền: chỉ HEAD đã tạo được xoá
        if (!t.getCreatedByHeadId().equals(headUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền xoá nhiệm vụ này");
        }

        if (t.getSubmissionArchiveId() != null) {
            t.setSubmissionArchiveId(null);
            taskRepo.save(t);
        }

        taskRepo.delete(t);
    }


    @Transactional
    public ExamTask updateByHead(Long headUserId, Long taskId, ExamTaskCreateDTO body) {
        ExamTask t = taskRepo.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        // chỉ cho HEAD đã tạo nó sửa
        if (!t.getCreatedByHeadId().equals(headUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền sửa nhiệm vụ này");
        }
        if (t.getStatus() == ExamTaskStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nhiệm vụ đã huỷ, không thể sửa");
        }
        // (tuỳ) khóa khi DONE:
        // if (t.getStatus() == ExamTaskStatus.DONE) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đã hoàn thành, không thể sửa");

        if (body.subjectId() != null && !body.subjectId().equals(t.getSubjectId())) {
            Subject subj = subjectRepo.findById(body.subjectId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Môn không tồn tại"));
            t.setSubjectId(subj.getId());
            t.setHeadDepartmentId(subj.getDepartment().getId()); // sync dept cache
        }
        if (body.assignedToId() != null)  t.setAssignedToId(body.assignedToId());
        if (body.title() != null)         t.setTitle(body.title());
        if (body.instructions() != null)  t.setInstructions(body.instructions().isBlank() ? null : body.instructions());
        if (body.structureJson() != null) t.setStructureJson(body.structureJson());
        if (body.dueAt() != null)         t.setDueAt(body.dueAt());

        ExamTask tmpTask = taskRepo.save(t);

        // NEW: thông báo cho giáo viên được giao task
        String headName = displayName(headUserId);
        String titleN = "Nhiệm vụ ra đề đã cập nhật";
        String msg = headName + " đã cập nhật nhiệm vụ: \"" + t.getTitle() + "\".";
        notif.create(
                t.getAssignedToId(),
                "Nhiệm vụ ra đề đã cập nhật",
                headName + " đã cập nhật nhiệm vụ: \"" + t.getTitle() + "\".",
                null,
                NotificationAction.TASK_UPDATED,
                NotificationTargetType.EXAM_TASK,
                t.getId(),
                null,
                null,
                AppArea.TEACHER
        );

        return tmpTask;
    }

    @Transactional
    public ExamTask teacherSetInProgress(Long teacherId, Long taskId) {
        ExamTask t = taskRepo.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!t.getAssignedToId().equals(teacherId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không phải nhiệm vụ của bạn");
        if (t.getStatus() == ExamTaskStatus.CANCELLED)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nhiệm vụ đã huỷ");

        if (t.getStatus() == ExamTaskStatus.ASSIGNED
                || t.getStatus() == ExamTaskStatus.REPORTED
                || t.getStatus() == ExamTaskStatus.RETURNED) {
            t.setStatus(ExamTaskStatus.IN_PROGRESS);
            t = taskRepo.save(t);

            String teacherName = displayName(teacherId);
            notif.create(
                    t.getCreatedByHeadId(),
                    "Giáo viên bắt đầu nhiệm vụ",
                    teacherName + " đã bắt đầu: \"" + t.getTitle() + "\".",
                    null,
                    NotificationAction.TASK_STARTED,
                    NotificationTargetType.EXAM_TASK,
                    t.getId(),
                    null,
                    null,
                    AppArea.ADMIN
            );
        }
        return t;
    }

    @Transactional
    public ExamTask teacherSubmit(Long teacherId, Long taskId,
                                  String fileName, String contentType, byte[] bytes,
                                  String note) throws Exception {
        ExamTask t = taskRepo.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!t.getAssignedToId().equals(teacherId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không phải nhiệm vụ của bạn");
        if (t.getStatus() == ExamTaskStatus.CANCELLED)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nhiệm vụ đã huỷ");

        // chỉ cho nộp từ ASSIGNED/IN_PROGRESS/REPORTED/SUBMITTED/RETURNED
        if (!(t.getStatus() == ExamTaskStatus.ASSIGNED
                || t.getStatus() == ExamTaskStatus.IN_PROGRESS
                || t.getStatus() == ExamTaskStatus.REPORTED
                || t.getStatus() == ExamTaskStatus.SUBMITTED
                || t.getStatus() == ExamTaskStatus.RETURNED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái hiện tại không cho phép nộp bài");
        }

        Map<String,Object> meta = Map.of(
                "variant","EXAM",
                "format","ZIP_DOCX",
                "taskId", t.getId(),
                "subjectId", t.getSubjectId()
        );

        // Cờ xác định có phải nộp lại hay không (đọc TRƯỚC khi thay submission)
        boolean isResubmit = (t.getSubmissionArchiveId() != null);

        // Thay thế submission cũ nếu cần
        var arch = fileArchiveService.replacePendingSubmission(
                t.getSubmissionArchiveId(),               // có thể null
                t.getSubjectId(), teacherId,
                fileName,
                (contentType == null ? "application/octet-stream" : contentType),
                bytes, meta
        );

        t.setSubmittedAt(Instant.now());
        t.setSubmissionArchiveId(arch.getId());
        t.setSubmissionNote((note == null || note.isBlank()) ? null : note);
        t.setStatus(ExamTaskStatus.SUBMITTED);
        t = taskRepo.save(t);

        String teacherName = displayName(teacherId);
        notif.create(
                t.getCreatedByHeadId(),
                "Giáo viên đã nộp bài",
                teacherName + " đã nộp" + (isResubmit ? " lại" : "") + " cho nhiệm vụ: \"" + t.getTitle() + "\".",
                null,
                NotificationAction.TASK_SUBMITTED,
                NotificationTargetType.EXAM_TASK,
                t.getId(),
                null,
                null,
                AppArea.HEAD
        );

        return t;
    }

    @Transactional
    public ExamTask teacherReport(Long teacherId, Long taskId, String note) {
        ExamTask t = taskRepo.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!t.getAssignedToId().equals(teacherId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không phải nhiệm vụ của bạn");
        if (t.getStatus() == ExamTaskStatus.CANCELLED)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nhiệm vụ đã huỷ");

        t.setReportedAt(Instant.now());
        t.setReportNote((note == null || note.isBlank()) ? null : note);
        t.setStatus(ExamTaskStatus.REPORTED);
        t = taskRepo.save(t);

        String teacherName = displayName(teacherId);
        notif.create(
                t.getCreatedByHeadId(),
                "Giáo viên báo lỗi nhiệm vụ",
                teacherName + " báo lỗi: \"" + t.getTitle() + "\"" +
                        (note == null || note.isBlank() ? "" : " — Ghi chú: " + note),
                null,
                NotificationAction.TASK_REPORTED,          // nhớ thêm vào enum NotificationAction
                NotificationTargetType.EXAM_TASK,
                t.getId(),
                null,
                null,
                AppArea.ADMIN
        );

        return t;
    }

    @Transactional
    public ExamTask headApproveDone(Long headUserId, Long taskId) {
        ExamTask t = taskRepo.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!Objects.equals(t.getCreatedByHeadId(), headUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền duyệt nhiệm vụ này");
        }

        ExamTaskStatus st = t.getStatus();
        if (st != ExamTaskStatus.SUBMITTED && st != ExamTaskStatus.RETURNED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Chỉ duyệt khi nhiệm vụ đang ở trạng thái ĐÃ NỘP hoặc BỊ TỪ CHỐI");
        }

        Long subId = t.getSubmissionArchiveId();
        if (subId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chưa có tệp nộp để duyệt");
        }

        // 1) Duyệt submission (move tmp/ -> archives/, mark APPROVED)
        fileArchiveService.approveSubmission(subId, headUserId);

        try {
            List<Long> usedIds = fileArchiveService.extractQuestionIdsFromSubmission(subId);
            if (!usedIds.isEmpty()) {
                questionMetaService.markUsed(usedIds); // cập nhật lastUsedAt & usageCount trong QuestionMeta
            }
        } catch (Exception e) {
            System.err.println("[WARN] Không thể cập nhật lastUsedAt/usageCount: " + e.getMessage());
        }

        // 3) Đánh dấu DONE cho task
        t.setStatus(ExamTaskStatus.DONE);
        t.setCompletedAt(Instant.now());
        t.setReviewedById(headUserId);
        t.setReviewedAt(Instant.now());
        t.setReviewNote(null);

        t = taskRepo.save(t);

        String headName = displayName(headUserId);
        notif.create(
                t.getAssignedToId(),
                "Nhiệm vụ đã được duyệt",
                headName + " đã duyệt hoàn thành nhiệm vụ: \"" + t.getTitle() + "\".",
                Instant.now().plusSeconds(30L * 24 * 3600),
                NotificationAction.TASK_APPROVED,
                NotificationTargetType.EXAM_TASK,
                t.getId(),
                null,
                null,
                AppArea.TEACHER
        );

        return t;
    }

    @Transactional
    public ExamTask headReturnForRevision(Long reviewerId, Long taskId, String reason) {
        var t = taskRepo.findById(taskId).orElseThrow();
        t.setStatus(ExamTaskStatus.RETURNED);
        t.setReviewedById(reviewerId);
        t.setReviewNote((reason == null || reason.isBlank()) ? null : reason);
        t.setReviewedAt(Instant.now());
        taskRepo.save(t);

        // notify teacher
        String headName = displayName(reviewerId);
        notif.create(
                t.getAssignedToId(),
                "Nhiệm vụ bị từ chối",
                headName + " đã từ chối và yêu cầu bạn nộp lại: \"" + t.getTitle() + "\""
                        + (reason == null || reason.isBlank() ? "" : " — Lý do: " + reason),
                null,
                NotificationAction.TASK_RETURNED,
                NotificationTargetType.EXAM_TASK,
                t.getId(),
                null,
                null,
                AppArea.TEACHER
        );

        return t;
    }
}
