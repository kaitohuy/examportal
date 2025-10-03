// com.exam.examserver.controller
package com.exam.examserver.controller;

import com.exam.examserver.dto.tasks.*;
import com.exam.examserver.enums.ExamTaskStatus;
import com.exam.examserver.enums.ViewScope;
import com.exam.examserver.model.exam.ExamTask;
import com.exam.examserver.model.exam.Subject;
import com.exam.examserver.model.user.CustomUserDetails;
import com.exam.examserver.model.user.User;
import com.exam.examserver.repo.*;
import com.exam.examserver.repo.spec.ExamTaskSpecs;
import com.exam.examserver.service.impl.ExamTaskService;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/exam-tasks")
@CrossOrigin("*")
public class ExamTaskController {

    private final ExamTaskService service;
    private final ExamTaskRepository taskRepo;
    private final SubjectRepository subjectRepo;
    private final UserRepository userRepo;
    private final DepartmentRepository deptRepo;

    public ExamTaskController(ExamTaskService service,
                              ExamTaskRepository taskRepo,
                              SubjectRepository subjectRepo,
                              UserRepository userRepo,
                              DepartmentRepository deptRepo) {
        this.service = service;
        this.taskRepo = taskRepo;
        this.subjectRepo = subjectRepo;
        this.userRepo = userRepo;
        this.deptRepo = deptRepo;
    }

    private Long uid(Authentication auth) {
        // giống controller khác của bạn: lấy từ CustomUserDetails
        return ((CustomUserDetails) auth.getPrincipal()).getId();
    }

    // ===== Create by HEAD =====
    @PostMapping
    public ExamTaskDTO create(Authentication auth, @RequestBody ExamTaskCreateDTO body) {
        Long headId = uid(auth);

        // resolve subject name
        Subject subj = subjectRepo.findById(body.subjectId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Môn không tồn tại"));

        ExamTask t = service.createByHead(
                headId,
                body.subjectId(),
                body.assignedToId(),
                body.title(),
                body.instructions(),
                body.structureJson(),
                body.dueAt()
        );

        var asgName = userRepo.findById(t.getAssignedToId()).map(ExamTaskController::displayName).orElse("");
        var headName= userRepo.findById(t.getCreatedByHeadId()).map(ExamTaskController::displayName).orElse("");

        String subjName = (subj.getCode()==null?"":subj.getCode()) +
                ((subj.getCode()!=null && subj.getName()!=null)?" - ":"") +
                (subj.getName()==null?"":subj.getName());

        return ExamTaskDTO.of(t, subjName, asgName, headName);
    }

    // ===== List (role-based)
    // Teacher: mặc định chỉ thấy task của mình.
    // Head: thấy task thuộc khoa mình.
    @GetMapping
    public Page<ExamTaskDTO> list(
            Authentication auth,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) ExamTaskStatus status,
            @RequestParam(required = false) String from,   // yyyy-MM-dd
            @RequestParam(required = false) String to,     // yyyy-MM-dd
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, defaultValue = "AUTO") ViewScope view
    ) {
        Long me = uid(auth);

        Pageable p = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Instant fromTs = parseDateStart(from);
        Instant toTs   = parseDateEnd(to);

        // Xác định nhanh vai trò hiện tại
        boolean isHead = deptRepo.existsByHeadUser_Id(me);

        // Scope mặc định
        Long assigneeId = null;
        Long creatorId  = null;

        switch (view) {
            case ASSIGNED -> assigneeId = me;
            case CREATED  -> creatorId  = me;
            case AUTO     -> {
                if (isHead) creatorId = me;  // HEAD xem task mình đã giao
                else assigneeId = me;        // Teacher xem task được giao cho mình
            }
        }

        // Build spec (các helper nằm ở ExamTaskSpecs như bạn đã thêm)

        Specification<ExamTask> spec = Specification.allOf(
                ExamTaskSpecs.scope(assigneeId, creatorId),
                ExamTaskSpecs.bySubject(subjectId),
                ExamTaskSpecs.byStatus(status),
                ExamTaskSpecs.createdBetween(fromTs, toTs)
        );

        Page<ExamTask> rs = taskRepo.findAll(spec, p);

        // batch resolve tên giống bản cũ
        Set<Long> subjIds = rs.getContent().stream().map(ExamTask::getSubjectId).collect(Collectors.toSet());
        Map<Long,String> subjMap = subjIds.isEmpty()? Map.of() :
                subjectRepo.findAllById(subjIds).stream().collect(Collectors.toMap(
                        Subject::getId, s -> (s.getCode()==null?"":s.getCode()) +
                                ((s.getCode()!=null && s.getName()!=null)?" - ":"") +
                                (s.getName()==null?"":s.getName())
                ));

        Set<Long> userIds = rs.getContent().stream()
                .flatMap(t -> Arrays.stream(new Long[]{t.getAssignedToId(), t.getCreatedByHeadId()}))
                .collect(Collectors.toSet());
        Map<Long,String> userMap = userIds.isEmpty()? Map.of() :
                userRepo.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, ExamTaskController::displayName));

        return rs.map(t -> ExamTaskDTO.of(
                t,
                subjMap.getOrDefault(t.getSubjectId(), ""),
                userMap.getOrDefault(t.getAssignedToId(), ""),
                userMap.getOrDefault(t.getCreatedByHeadId(), "")
        ));
    }

    // ===== Teacher cập nhật trạng thái
    @PostMapping("/{id}/status")
    public ExamTaskDTO updateStatus(Authentication auth,
                                    @PathVariable Long id,
                                    @RequestBody ExamTaskUpdateStatusDTO body) {
        Long me = uid(auth);
        ExamTask t = service.updateStatusByTeacher(me, id, body.status());

        // map tên
        var subjName = subjectRepo.findById(t.getSubjectId())
                .map(s -> (s.getCode()==null?"":s.getCode()) + ((s.getCode()!=null && s.getName()!=null)?" - ":"") + (s.getName()==null?"":s.getName()))
                .orElse("");
        var asgName = userRepo.findById(t.getAssignedToId()).map(ExamTaskController::displayName).orElse("");
        var headName= userRepo.findById(t.getCreatedByHeadId()).map(ExamTaskController::displayName).orElse("");
        return ExamTaskDTO.of(t, subjName, asgName, headName);
    }

    // ===== HEAD huỷ nhiệm vụ
    @PostMapping("/{id}/cancel")
    public ExamTaskDTO cancel(Authentication auth, @PathVariable Long id) {
        Long me = uid(auth);
        ExamTask t = service.cancelByHead(me, id);

        var subjName = subjectRepo.findById(t.getSubjectId())
                .map(s -> (s.getCode()==null?"":s.getCode()) + ((s.getCode()!=null && s.getName()!=null)?" - ":"") + (s.getName()==null?"":s.getName()))
                .orElse("");
        var asgName = userRepo.findById(t.getAssignedToId()).map(ExamTaskController::displayName).orElse("");
        var headName= userRepo.findById(t.getCreatedByHeadId()).map(ExamTaskController::displayName).orElse("");
        return ExamTaskDTO.of(t, subjName, asgName, headName);
    }

    // helpers
    private static Instant parseDateStart(String yyyyMMdd) {
        if (yyyyMMdd == null || yyyyMMdd.isBlank()) return null;
        return LocalDate.parse(yyyyMMdd).atStartOfDay(ZoneId.systemDefault()).toInstant();
    }
    private static Instant parseDateEnd(String yyyyMMdd) {
        if (yyyyMMdd == null || yyyyMMdd.isBlank()) return null;
        return LocalDate.parse(yyyyMMdd).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
    }
    private static String displayName(User u) {
        String f = Optional.ofNullable(u.getFirstName()).orElse("").trim();
        String l = Optional.ofNullable(u.getLastName()).orElse("").trim();
        String full1 = (f + " " + l).trim();
        String full2 = (l + " " + f).trim();
        if (!full1.isBlank()) return full1;
        if (!full2.isBlank()) return full2;
        if (u.getUsername()!=null && !u.getUsername().isBlank()) return u.getUsername();
        if (u.getEmail()!=null && !u.getEmail().isBlank()) return u.getEmail();
        return "User #" + u.getId();
    }

    @PutMapping("/{id}")
    public ExamTaskDTO update(Authentication auth, @PathVariable Long id, @RequestBody ExamTaskCreateDTO body) {
        Long headId = uid(auth);
        // xác thực là HEAD (đang làm đúng scope "mình đã giao")
        deptRepo.findByHeadUser_Id(headId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không phải HEAD"));

        ExamTask t = service.updateByHead(headId, id, body);

        var subjName = subjectRepo.findById(t.getSubjectId())
                .map(s -> (s.getCode()==null?"":s.getCode()) +
                        ((s.getCode()!=null && s.getName()!=null)?" - ":"") +
                        (s.getName()==null?"":s.getName()))
                .orElse("");
        var asgName = userRepo.findById(t.getAssignedToId()).map(ExamTaskController::displayName).orElse("");
        var headName= userRepo.findById(t.getCreatedByHeadId()).map(ExamTaskController::displayName).orElse("");

        return ExamTaskDTO.of(t, subjName, asgName, headName);
    }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable Long id) {
        Long headId = uid(auth);
        // xác thực có vai trò HEAD (giống các chỗ khác)
        deptRepo.findByHeadUser_Id(headId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không phải HEAD"));
        service.deleteByHead(headId, id);
    }

}
