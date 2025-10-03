package com.exam.examserver.service.impl;

import com.exam.examserver.dto.tasks.ExamTaskCreateDTO;
import com.exam.examserver.enums.ExamTaskStatus;
import com.exam.examserver.model.Notification;
import com.exam.examserver.model.exam.ExamTask;
import com.exam.examserver.model.exam.Subject;
import com.exam.examserver.model.user.User;
import com.exam.examserver.repo.*;
import com.exam.examserver.service.impl.NotificationService;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
public class ExamTaskService {

    private final ExamTaskRepository taskRepo;
    private final SubjectRepository subjectRepo;
    private final UserRepository userRepo;
    private final DepartmentRepository deptRepo;
    private final NotificationService notif;

    public ExamTaskService(ExamTaskRepository taskRepo,
                           SubjectRepository subjectRepo,
                           UserRepository userRepo,
                           DepartmentRepository deptRepo,
                           NotificationService notif) {
        this.taskRepo = taskRepo;
        this.subjectRepo = subjectRepo;
        this.userRepo = userRepo;
        this.deptRepo = deptRepo;
        this.notif = notif;
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
        notif.create(assignedToId, titleN, msg, null);

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
        } else if (next == ExamTaskStatus.DONE) {
            t.setStatus(ExamTaskStatus.DONE);
            t.setCompletedAt(Instant.now());

            // Notify HEAD (người tạo)
            String teacherName = displayName(teacherId);
            String titleN = "Nhiệm vụ đã hoàn thành";
            String msg = "Giáo viên " + teacherName + " đã hoàn thành nhiệm vụ: \"" + t.getTitle() + "\".";
            notif.create(t.getCreatedByHeadId(), titleN, msg, Instant.now().plusSeconds(30L*24*3600));
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái không hợp lệ");
        }

        return taskRepo.save(t);
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
        notif.create(t.getAssignedToId(), titleN, msg, null);

        return t;
    }

    @Transactional
    public void deleteByHead(Long headUserId, Long taskId) {
        ExamTask t = taskRepo.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // chỉ HEAD đã tạo mới được xoá
        if (!t.getCreatedByHeadId().equals(headUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền xoá nhiệm vụ này");
        }
        // chỉ xoá khi đã huỷ
        if (t.getStatus() != ExamTaskStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ có thể xoá nhiệm vụ đã huỷ");
        }

        taskRepo.deleteById(taskId);
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
        notif.create(t.getAssignedToId(), titleN, msg, null);

        return tmpTask;
    }

}
