package com.exam.examserver.service.impl;

import com.exam.examserver.dto.importing.AdminOverviewDto;
import com.exam.examserver.dto.importing.HeadOverviewDto;
import com.exam.examserver.dto.importing.TeacherOverviewDto;
import com.exam.examserver.enums.Difficulty;
import com.exam.examserver.enums.QuestionLabel;
import com.exam.examserver.enums.ReviewStatus;
import com.exam.examserver.enums.RoleType;
import com.exam.examserver.repo.DepartmentRepository;
import com.exam.examserver.repo.FileArchiveRepository;
import com.exam.examserver.repo.QuestionRepository;
import com.exam.examserver.repo.SubjectRepository;
import com.exam.examserver.repo.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StatsService {

    private final UserRepository userRepo;
    private final DepartmentRepository deptRepo;
    private final SubjectRepository subjRepo;
    private final QuestionRepository questionRepo;
    private final FileArchiveRepository faRepo;

    @PersistenceContext
    private EntityManager em;

    public StatsService(UserRepository userRepo,
                        DepartmentRepository deptRepo,
                        SubjectRepository subjRepo,
                        QuestionRepository questionRepo,
                        FileArchiveRepository faRepo) {
        this.userRepo = userRepo;
        this.deptRepo = deptRepo;
        this.subjRepo = subjRepo;
        this.questionRepo = questionRepo;
        this.faRepo = faRepo;
    }

    @Transactional(readOnly = true)
    public AdminOverviewDto getAdminOverview(LocalDate fromDate, LocalDate toDate) {
        // ===== Chuẩn hoá khoảng thời gian =====
        // Question.createdAt = LocalDateTime -> dùng [fromLdt, toLdt)
        // FileArchive.createdAt = Instant     -> dùng [fromInst, toInst)
        LocalDateTime fromLdt = (fromDate != null) ? fromDate.atStartOfDay() : null;
        LocalDateTime toLdt   = (toDate   != null) ? toDate.plusDays(1).atStartOfDay() : null;

        ZoneId zone = ZoneId.systemDefault();
        Instant fromInst = (fromLdt != null) ? fromLdt.atZone(zone).toInstant() : null;
        Instant toInst   = (toLdt   != null) ? toLdt.atZone(zone).toInstant()   : null;

        var dto = new AdminOverviewDto();

        // ===== Users =====
        dto.users.total = userRepo.count();
        dto.users.byRole.put("ADMIN",   safeRoleCount(RoleType.ADMIN));
        dto.users.byRole.put("HEAD",    safeRoleCount(RoleType.HEAD));
        dto.users.byRole.put("TEACHER", safeRoleCount(RoleType.TEACHER));

        // ===== Departments =====
        dto.departments.count = deptRepo.count();
        dto.departments.withoutHead = countDepartmentsWithoutHead();

        // ===== Subjects =====
        dto.subjects.count = subjRepo.count();
        dto.subjects.withoutTeachers = countSubjectsWithoutTeachers();

        // ===== Questions =====
        // Tổng số (theo khoảng thời gian nếu có)
        dto.questions.total = countQuestions(fromLdt, toLdt);

        // Theo loại câu hỏi (MULTIPLE_CHOICE / ESSAY) — group-by bằng EntityManager
        Map<String, Long> byType = groupQuestionsByType(fromLdt, toLdt);
        dto.questions.byType.putAll(byType);

        // Theo nhãn (PRACTICE / EXAM) — group-by bằng EntityManager (join labels)
        Map<String, Long> byLabel = groupQuestionsByLabel(fromLdt, toLdt);
        dto.questions.byLabel.putAll(byLabel);

        // Coverage theo chapter 0..7 — group-by chapter
        Map<Integer, Long> byChapter = groupQuestionsByChapter(fromLdt, toLdt);
        for (int c = 0; c <= 7; c++) {
            long cnt = byChapter.getOrDefault(c, 0L);
            dto.coverage.put("chapter" + c, cnt);
        }

        // (Tuỳ chọn) Nếu bạn cần breakdown theo độ khó:
        // Không bắt buộc cho FE hiện tại, nhưng để sẵn ví dụ:
        dto.questions.byDifficulty = new EnumMap<>(Difficulty.class);
        for (Difficulty d : Difficulty.values()) {
            dto.questions.byDifficulty.put(d, countQuestionsByDifficulty(d, fromLdt, toLdt));
        }

        // ===== Archive (FileArchive) =====
        dto.archive.pending  = countArchiveByStatus(ReviewStatus.PENDING,  fromInst, toInst);
        dto.archive.approved = countArchiveByStatus(ReviewStatus.APPROVED, fromInst, toInst);
        dto.archive.rejected = countArchiveByStatus(ReviewStatus.REJECTED, fromInst, toInst);

        // Thời gian duyệt trung bình (giờ)
        double avgHours = averageReviewHours(fromInst, toInst);
        dto.archive.avgReviewHours = Math.round(avgHours * 10.0) / 10.0;

        return dto;
    }

    @Transactional(readOnly = true)
    public HeadOverviewDto getHeadOverview(Long deptId, LocalDate fromDate, LocalDate toDate) {
        Long finalDeptId = resolveDeptId(deptId);
        if (finalDeptId == null) throw new IllegalStateException("Không tìm thấy khoa cho HEAD hiện tại");

        // Chuẩn hoá khoảng thời gian
        LocalDateTime fromLdt = (fromDate != null) ? fromDate.atStartOfDay() : null;
        LocalDateTime toLdt   = (toDate   != null) ? toDate.plusDays(1).atStartOfDay() : null;
        ZoneId zone = ZoneId.systemDefault();
        Instant fromInst = (fromLdt != null) ? fromLdt.atZone(zone).toInstant() : null;
        Instant toInst   = (toLdt   != null) ? toLdt.atZone(zone).toInstant()   : null;

        HeadOverviewDto dto = new HeadOverviewDto();

        // ===== Users (trong khoa) =====
        dto.users.total = userRepo.countByDepartment_Id(finalDeptId);
        dto.users.byRole.put("ADMIN",   userRepo.countByRoleAndDepartment(RoleType.ADMIN,   finalDeptId));
        dto.users.byRole.put("HEAD",    userRepo.countByRoleAndDepartment(RoleType.HEAD,    finalDeptId));
        dto.users.byRole.put("TEACHER", userRepo.countByRoleAndDepartment(RoleType.TEACHER, finalDeptId));

        // ===== Departments (chỉ 1 khoa) =====
        dto.departments.count = 1;
        dto.departments.withoutHead = deptHasHead(finalDeptId) ? 0 : 1;

        // ===== Subjects (trong khoa) =====
        dto.subjects.count = subjRepo.countByDepartmentId(finalDeptId);
        dto.subjects.withoutTeachers = subjRepo.countUnassignedByDepartmentId(finalDeptId);

        // ===== Questions (giới hạn theo khoa) =====
        dto.questions.total = countQuestionsInDept(finalDeptId, fromLdt, toLdt);
        dto.questions.byType.putAll(groupQuestionsByTypeInDept(finalDeptId, fromLdt, toLdt));
        dto.questions.byLabel.putAll(groupQuestionsByLabelInDept(finalDeptId, fromLdt, toLdt));
        Map<Integer, Long> byChapter = groupQuestionsByChapterInDept(finalDeptId, fromLdt, toLdt);
        for (int c = 0; c <= 7; c++) dto.coverage.put("chapter" + c, byChapter.getOrDefault(c, 0L));

        // (tuỳ chọn) theo độ khó
        for (Difficulty d : Difficulty.values()) {
            dto.questions.byDifficulty.put(d, countQuestionsByDifficultyInDept(finalDeptId, d, fromLdt, toLdt));
        }

        // ===== Archive (FileArchive) trong khoa =====
        List<Long> subjectIds = findSubjectIdsByDept(finalDeptId);
        if (subjectIds.isEmpty()) {
            dto.archive.pending = dto.archive.approved = dto.archive.rejected = 0;
            dto.archive.avgReviewHours = 0;
        } else {
            dto.archive.pending  = countArchiveByStatusForSubjects(ReviewStatus.PENDING,  subjectIds, fromInst, toInst);
            dto.archive.approved = countArchiveByStatusForSubjects(ReviewStatus.APPROVED, subjectIds, fromInst, toInst);
            dto.archive.rejected = countArchiveByStatusForSubjects(ReviewStatus.REJECTED, subjectIds, fromInst, toInst);
            dto.archive.avgReviewHours = Math.round(
                    averageReviewHoursForSubjects(subjectIds, fromInst, toInst) * 10.0) / 10.0;
        }

        // ===== Top lists =====
        dto.topSubjects = topSubjectsInDept(finalDeptId, fromLdt, toLdt, 5);
        dto.topTeachers = topTeachersInDept(finalDeptId, fromLdt, toLdt, 5);

        return dto;
    }

    // ---------- helper: lấy deptId từ SecurityContext nếu không truyền ----------
    private Long resolveDeptId(Long deptId) {
        if (deptId != null) return deptId;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;

        String username = auth.getName();
        return userRepo.findByUsername(username)
                .flatMap(u -> deptRepo.findByHeadUser_Id(u.getId()))
                .map(d -> d.getId())
                .orElse(null);
    }

    private boolean deptHasHead(Long deptId) {
        return deptRepo.findById(deptId).map(d -> d.getHeadUser() != null).orElse(false);
    }

    private List<Long> findSubjectIdsByDept(Long deptId) {
        return em.createQuery("select s.id from Subject s where s.department.id = :deptId", Long.class)
                .setParameter("deptId", deptId)
                .getResultList();
    }

    // ---------- Questions (by dept) ----------
    private long countQuestionsInDept(Long deptId, LocalDateTime from, LocalDateTime to) {
        StringBuilder jpql = new StringBuilder(
                "select count(q) from Question q where q.subject.department.id = :deptId");
        if (from != null) jpql.append(" and q.createdAt >= :from");
        if (to != null)   jpql.append(" and q.createdAt < :to");

        var q = em.createQuery(jpql.toString(), Long.class)
                .setParameter("deptId", deptId);
        if (from != null) q.setParameter("from", from);
        if (to != null)   q.setParameter("to", to);
        return q.getSingleResult();
    }

    private Map<String, Long> groupQuestionsByTypeInDept(Long deptId, LocalDateTime from, LocalDateTime to) {
        StringBuilder jpql = new StringBuilder(
                "select q.questionType, count(q) from Question q " +
                        "where q.subject.department.id = :deptId");
        if (from != null) jpql.append(" and q.createdAt >= :from");
        if (to != null)   jpql.append(" and q.createdAt < :to");
        jpql.append(" group by q.questionType");

        var q = em.createQuery(jpql.toString(), Object[].class)
                .setParameter("deptId", deptId);
        if (from != null) q.setParameter("from", from);
        if (to != null)   q.setParameter("to", to);

        Map<String, Long> res = new HashMap<>();
        for (Object[] r : q.getResultList()) res.put(String.valueOf(r[0]), ((Number) r[1]).longValue());
        return res;
    }

    private Map<String, Long> groupQuestionsByLabelInDept(Long deptId, LocalDateTime from, LocalDateTime to) {
        StringBuilder jpql = new StringBuilder(
                "select l, count(q) from Question q join q.labels l " +
                        "where q.subject.department.id = :deptId");
        if (from != null) jpql.append(" and q.createdAt >= :from");
        if (to != null)   jpql.append(" and q.createdAt < :to");
        jpql.append(" group by l");

        var q = em.createQuery(jpql.toString(), Object[].class)
                .setParameter("deptId", deptId);
        if (from != null) q.setParameter("from", from);
        if (to != null)   q.setParameter("to", to);

        Map<String, Long> res = new HashMap<>();
        for (Object[] r : q.getResultList()) res.put(String.valueOf(r[0]), ((Number) r[1]).longValue());
        return res;
    }

    private Map<Integer, Long> groupQuestionsByChapterInDept(Long deptId, LocalDateTime from, LocalDateTime to) {
        StringBuilder jpql = new StringBuilder(
                "select q.chapter, count(q) from Question q " +
                        "where q.subject.department.id = :deptId");
        if (from != null) jpql.append(" and q.createdAt >= :from");
        if (to != null)   jpql.append(" and q.createdAt < :to");
        jpql.append(" group by q.chapter");

        var q = em.createQuery(jpql.toString(), Object[].class)
                .setParameter("deptId", deptId);
        if (from != null) q.setParameter("from", from);
        if (to != null)   q.setParameter("to", to);

        Map<Integer, Long> res = new HashMap<>();
        for (Object[] r : q.getResultList()) {
            Integer chap = (r[0] == null) ? 0 : ((Number) r[0]).intValue();
            res.put(chap, ((Number) r[1]).longValue());
        }
        return res;
    }

    private long countQuestionsByDifficultyInDept(Long deptId, Difficulty diff, LocalDateTime from, LocalDateTime to) {
        StringBuilder jpql = new StringBuilder(
                "select count(q) from Question q where q.subject.department.id = :deptId and q.difficulty = :d");
        if (from != null) jpql.append(" and q.createdAt >= :from");
        if (to != null)   jpql.append(" and q.createdAt < :to");

        var q = em.createQuery(jpql.toString(), Long.class)
                .setParameter("deptId", deptId)
                .setParameter("d", diff);
        if (from != null) q.setParameter("from", from);
        if (to != null)   q.setParameter("to", to);
        return q.getSingleResult();
    }

    // ---------- FileArchive (by subjectIds in dept) ----------
    private long countArchiveByStatusForSubjects(ReviewStatus st, List<Long> subjectIds, Instant from, Instant to) {
        StringBuilder jpql = new StringBuilder(
                "select count(f) from FileArchive f where f.reviewStatus = :st and f.subjectId in :ids");
        if (from != null) jpql.append(" and f.createdAt >= :from");
        if (to != null)   jpql.append(" and f.createdAt < :to");

        var q = em.createQuery(jpql.toString(), Long.class)
                .setParameter("st", st)
                .setParameter("ids", subjectIds);
        if (from != null) q.setParameter("from", from);
        if (to != null)   q.setParameter("to", to);
        return q.getSingleResult();
    }

    private double averageReviewHoursForSubjects(List<Long> subjectIds, Instant from, Instant to) {
        StringBuilder jpql = new StringBuilder(
                "select f.createdAt, f.reviewedAt from FileArchive f " +
                        "where f.reviewedAt is not null and f.subjectId in :ids");
        if (from != null) jpql.append(" and f.createdAt >= :from");
        if (to != null)   jpql.append(" and f.createdAt < :to");

        var q = em.createQuery(jpql.toString(), Object[].class)
                .setParameter("ids", subjectIds);
        if (from != null) q.setParameter("from", from);
        if (to != null)   q.setParameter("to", to);

        List<Object[]> rows = q.getResultList();
        if (rows.isEmpty()) return 0d;

        double sum = 0d; int n = 0;
        for (Object[] r : rows) {
            Instant c = (Instant) r[0];
            Instant rv = (Instant) r[1];
            if (c != null && rv != null) { sum += Duration.between(c, rv).toMillis() / 3600000.0; n++; }
        }
        return (n == 0) ? 0d : sum / n;
    }

    // ---------- Top lists ----------
    private List<HeadOverviewDto.TopSubject> topSubjectsInDept(Long deptId, LocalDateTime from, LocalDateTime to, int limit) {
        StringBuilder jpql = new StringBuilder(
                "select q.subject.id, q.subject.name, count(q) " +
                        "from Question q where q.subject.department.id = :deptId");
        if (from != null) jpql.append(" and q.createdAt >= :from");
        if (to != null)   jpql.append(" and q.createdAt < :to");
        jpql.append(" group by q.subject.id, q.subject.name order by count(q) desc");

        var q = em.createQuery(jpql.toString(), Object[].class)
                .setParameter("deptId", deptId)
                .setMaxResults(limit);

        if (from != null) q.setParameter("from", from);
        if (to != null)   q.setParameter("to", to);

        return q.getResultList().stream()
                .map(r -> new HeadOverviewDto.TopSubject(
                        ((Number) r[0]).longValue(),
                        (String) r[1],
                        ((Number) r[2]).longValue()))
                .collect(Collectors.toList());
    }

    private List<HeadOverviewDto.TopTeacher> topTeachersInDept(Long deptId, LocalDateTime from, LocalDateTime to, int limit) {
        StringBuilder jpql = new StringBuilder(
                "select q.createdBy.id, " +
                        "concat(coalesce(q.createdBy.firstName,''),' ',coalesce(q.createdBy.lastName,'')), " +
                        "count(q) from Question q " +
                        "where q.subject.department.id = :deptId and q.createdBy.id is not null");
        if (from != null) jpql.append(" and q.createdAt >= :from");
        if (to != null)   jpql.append(" and q.createdAt < :to");
        jpql.append(" group by q.createdBy.id, q.createdBy.firstName, q.createdBy.lastName " +
                "order by count(q) desc");

        var q = em.createQuery(jpql.toString(), Object[].class)
                .setParameter("deptId", deptId)
                .setMaxResults(limit);

        if (from != null) q.setParameter("from", from);
        if (to != null)   q.setParameter("to", to);

        return q.getResultList().stream()
                .map(r -> new HeadOverviewDto.TopTeacher(
                        ((Number) r[0]).longValue(),
                        (String) r[1],
                        ((Number) r[2]).longValue()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TeacherOverviewDto getTeacherOverview(Long userIdParam, LocalDate fromDate, LocalDate toDate) {
        Long userId = resolveUserId(userIdParam);
        if (userId == null) throw new IllegalStateException("Không xác định được giáo viên");

        // chuẩn hoá thời gian
        LocalDateTime fromLdt = (fromDate != null) ? fromDate.atStartOfDay() : null;
        LocalDateTime toLdt   = (toDate   != null) ? toDate.plusDays(1).atStartOfDay() : null;
        ZoneId zone = ZoneId.systemDefault();
        Instant fromInst = (fromLdt != null) ? fromLdt.atZone(zone).toInstant() : null;
        Instant toInst   = (toLdt   != null) ? toLdt.atZone(zone).toInstant()   : null;

        TeacherOverviewDto dto = new TeacherOverviewDto();

        // ===== Subjects assigned =====
        dto.subjects.assigned = countAssignedSubjects(userId);
        dto.subjects.myContribTop = topSubjectContrib(userId, fromLdt, toLdt, 5);

        // ===== Questions (createdBy = user) =====
        dto.questions.total = countMyQuestions(userId, fromLdt, toLdt);
        dto.questions.byType.putAll(groupMyQuestionsByType(userId, fromLdt, toLdt));
        dto.questions.byLabel.putAll(groupMyQuestionsByLabel(userId, fromLdt, toLdt));
        for (var d : Difficulty.values()) {
            dto.questions.byDifficulty.put(d, countMyQuestionsByDifficulty(userId, d, fromLdt, toLdt));
        }
        Map<Integer, Long> byChap = groupMyQuestionsByChapter(userId, fromLdt, toLdt);
        for (int c = 0; c <= 7; c++) dto.coverage.put("chapter" + c, byChap.getOrDefault(c, 0L));

        // ===== My archives =====
        dto.archive.pending  = countMyArchiveByStatus(userId, ReviewStatus.PENDING,  fromInst, toInst);
        dto.archive.approved = countMyArchiveByStatus(userId, ReviewStatus.APPROVED, fromInst, toInst);
        dto.archive.rejected = countMyArchiveByStatus(userId, ReviewStatus.REJECTED, fromInst, toInst);
        dto.archive.avgReviewHours = Math.round(averageMyReviewHours(userId, fromInst, toInst) * 10.0) / 10.0;
        dto.archive.pendingSoon = findMyPendingSoon(userId, 5);

        return dto;
    }

    // -------------------- resolve current user id --------------------
    private Long resolveUserId(Long userIdParam) {
        if (userIdParam != null) return userIdParam;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        return userRepo.findByUsername(auth.getName()).map(u -> u.getId()).orElse(null);
    }

    // -------------------- Subjects assigned count & contrib --------------------
    private long countAssignedSubjects(Long userId) {
        // đếm số môn có TeacherSubject.teacher = userId
        String jpql = "select count(distinct s.id) from Subject s join s.teacherSubjects ts where ts.teacher.id = :uid";
        return em.createQuery(jpql, Long.class).setParameter("uid", userId).getSingleResult();
    }

    private List<TeacherOverviewDto.SubjectContribution> topSubjectContrib(
            Long userId, LocalDateTime from, LocalDateTime to, int limit) {

        StringBuilder jpql = new StringBuilder(
                "select q.subject.id, q.subject.name, " +
                        "count(q), (select count(q2) from Question q2 where q2.subject.id = q.subject.id) " +
                        "from Question q where q.createdBy.id = :uid");
        if (from != null) jpql.append(" and q.createdAt >= :from");
        if (to != null)   jpql.append(" and q.createdAt < :to");
        jpql.append(" group by q.subject.id, q.subject.name order by count(q) desc");

        var q = em.createQuery(jpql.toString(), Object[].class)
                .setParameter("uid", userId)
                .setMaxResults(limit);
        if (from != null) q.setParameter("from", from);
        if (to != null)   q.setParameter("to", to);

        List<Object[]> rows = q.getResultList();
        List<TeacherOverviewDto.SubjectContribution> res = new ArrayList<>();
        for (Object[] r : rows) {
            res.add(new TeacherOverviewDto.SubjectContribution(
                    ((Number) r[0]).longValue(),
                    (String) r[1],
                    ((Number) r[2]).longValue(),
                    ((Number) r[3]).longValue()
            ));
        }
        return res;
    }

    // -------------------- Questions (mine) --------------------
    private long countMyQuestions(Long userId, LocalDateTime from, LocalDateTime to) {
        StringBuilder jpql = new StringBuilder(
                "select count(q) from Question q where q.createdBy.id = :uid");
        if (from != null) jpql.append(" and q.createdAt >= :from");
        if (to != null)   jpql.append(" and q.createdAt < :to");

        var q = em.createQuery(jpql.toString(), Long.class).setParameter("uid", userId);
        if (from != null) q.setParameter("from", from);
        if (to != null)   q.setParameter("to", to);
        return q.getSingleResult();
    }

    private Map<String, Long> groupMyQuestionsByType(Long userId, LocalDateTime from, LocalDateTime to) {
        StringBuilder jpql = new StringBuilder(
                "select q.questionType, count(q) from Question q where q.createdBy.id = :uid");
        if (from != null) jpql.append(" and q.createdAt >= :from");
        if (to != null)   jpql.append(" and q.createdAt < :to");
        jpql.append(" group by q.questionType");

        var q = em.createQuery(jpql.toString(), Object[].class).setParameter("uid", userId);
        if (from != null) q.setParameter("from", from);
        if (to != null)   q.setParameter("to", to);

        Map<String, Long> res = new HashMap<>();
        for (Object[] r : q.getResultList()) res.put(String.valueOf(r[0]), ((Number) r[1]).longValue());
        return res;
    }

    private Map<String, Long> groupMyQuestionsByLabel(Long userId, LocalDateTime from, LocalDateTime to) {
        StringBuilder jpql = new StringBuilder(
                "select l, count(q) from Question q join q.labels l where q.createdBy.id = :uid");
        if (from != null) jpql.append(" and q.createdAt >= :from");
        if (to != null)   jpql.append(" and q.createdAt < :to");
        jpql.append(" group by l");

        var q = em.createQuery(jpql.toString(), Object[].class).setParameter("uid", userId);
        if (from != null) q.setParameter("from", from);
        if (to != null)   q.setParameter("to", to);

        Map<String, Long> res = new HashMap<>();
        for (Object[] r : q.getResultList()) res.put(String.valueOf(r[0]), ((Number) r[1]).longValue());
        return res;
    }

    private Map<Integer, Long> groupMyQuestionsByChapter(Long userId, LocalDateTime from, LocalDateTime to) {
        StringBuilder jpql = new StringBuilder(
                "select q.chapter, count(q) from Question q where q.createdBy.id = :uid");
        if (from != null) jpql.append(" and q.createdAt >= :from");
        if (to != null)   jpql.append(" and q.createdAt < :to");
        jpql.append(" group by q.chapter");

        var q = em.createQuery(jpql.toString(), Object[].class).setParameter("uid", userId);
        if (from != null) q.setParameter("from", from);
        if (to != null)   q.setParameter("to", to);

        Map<Integer, Long> res = new HashMap<>();
        for (Object[] r : q.getResultList()) {
            Integer chap = (r[0] == null) ? 0 : ((Number) r[0]).intValue();
            res.put(chap, ((Number) r[1]).longValue());
        }
        return res;
    }

    private long countMyQuestionsByDifficulty(Long userId, Difficulty d, LocalDateTime from, LocalDateTime to) {
        StringBuilder jpql = new StringBuilder(
                "select count(q) from Question q where q.createdBy.id = :uid and q.difficulty = :d");
        if (from != null) jpql.append(" and q.createdAt >= :from");
        if (to != null)   jpql.append(" and q.createdAt < :to");

        var q = em.createQuery(jpql.toString(), Long.class)
                .setParameter("uid", userId)
                .setParameter("d", d);
        if (from != null) q.setParameter("from", from);
        if (to != null)   q.setParameter("to", to);
        return q.getSingleResult();
    }

    // -------------------- FileArchive (mine) --------------------
    private long countMyArchiveByStatus(Long userId, ReviewStatus st, Instant from, Instant to) {
        StringBuilder jpql = new StringBuilder(
                "select count(f) from FileArchive f where f.userId = :uid and f.reviewStatus = :st");
        if (from != null) jpql.append(" and f.createdAt >= :from");
        if (to != null)   jpql.append(" and f.createdAt < :to");

        var q = em.createQuery(jpql.toString(), Long.class)
                .setParameter("uid", userId)
                .setParameter("st", st);
        if (from != null) q.setParameter("from", from);
        if (to != null)   q.setParameter("to", to);
        return q.getSingleResult();
    }

    private double averageMyReviewHours(Long userId, Instant from, Instant to) {
        StringBuilder jpql = new StringBuilder(
                "select f.createdAt, f.reviewedAt from FileArchive f " +
                        "where f.userId = :uid and f.reviewedAt is not null");
        if (from != null) jpql.append(" and f.createdAt >= :from");
        if (to != null)   jpql.append(" and f.createdAt < :to");

        var q = em.createQuery(jpql.toString(), Object[].class).setParameter("uid", userId);
        if (from != null) q.setParameter("from", from);
        if (to != null)   q.setParameter("to", to);

        var rows = q.getResultList();
        if (rows.isEmpty()) return 0d;

        double sumHours = 0d; int n = 0;
        for (Object[] r : rows) {
            Instant c = (Instant) r[0], rv = (Instant) r[1];
            if (c != null && rv != null) { sumHours += Duration.between(c, rv).toMillis() / 3600000.0; n++; }
        }
        return (n == 0) ? 0d : sumHours / n;
    }

    private List<TeacherOverviewDto.PendingItem> findMyPendingSoon(Long userId, int limit) {
        String jpql =
                "select f.id, f.filename, cast(f.variant as string), cast(f.reviewStatus as string), " +
                        "f.createdAt, f.reviewDeadline, s.name " +
                        "from FileArchive f left join Subject s on s.id = f.subjectId " +
                        "where f.userId = :uid and f.reviewStatus = com.exam.examserver.enums.ReviewStatus.PENDING " +
                        "order by (case when f.reviewDeadline is null then 1 else 0 end), f.reviewDeadline asc, f.createdAt asc";

        var q = em.createQuery(jpql, Object[].class)
                .setParameter("uid", userId)
                .setMaxResults(limit);

        List<TeacherOverviewDto.PendingItem> res = new ArrayList<>();
        for (Object[] r : q.getResultList()) {
            res.add(new TeacherOverviewDto.PendingItem(
                    ((Number) r[0]).longValue(),
                    (String) r[1],
                    (String) r[2],
                    (String) r[3],
                    (Instant) r[4],
                    (Instant) r[5],
                    (String) r[6]
            ));
        }
        return res;
    }

    // =====================================================================
    // Users/Departments/Subjects helpers
    // =====================================================================

    private long safeRoleCount(RoleType role) {
        try {
            return userRepo.countByUserRoles_Role_RoleName(role);
        } catch (Exception ex) {
            return 0L; // tránh 500 nếu schema/seed role chưa khớp
        }
    }

    private long countDepartmentsWithoutHead() {
        // JPQL đơn giản để không phụ thuộc repo tuỳ biến
        TypedQuery<Long> q = em.createQuery(
                "select count(d) from Department d where d.headUser is null", Long.class);
        return q.getSingleResult();
    }

    private long countSubjectsWithoutTeachers() {
        // Subject không có TeacherSubject nào
        TypedQuery<Long> q = em.createQuery(
                "select count(s) from Subject s left join s.teacherSubjects ts where ts.id is null", Long.class);
        return q.getSingleResult();
    }

    // =====================================================================
    // Questions helpers
    // =====================================================================

    private long countQuestions(LocalDateTime from, LocalDateTime to) {
        if (from == null && to == null) return questionRepo.count();
        if (from != null && to != null) return questionRepo.countByCreatedAtBetween(from, to);
        if (from != null) return questionRepo.countByCreatedAtGreaterThanEqual(from);
        return questionRepo.countByCreatedAtLessThan(to);
    }

    private long countQuestionsByDifficulty(Difficulty d, LocalDateTime from, LocalDateTime to) {
        if (from == null && to == null) return questionRepo.countByDifficulty(d);
        if (from != null && to != null) return questionRepo.countByDifficultyAndCreatedAtBetween(d, from, to);
        if (from != null) return questionRepo.countByDifficultyAndCreatedAtGreaterThanEqual(d, from);
        return questionRepo.countByDifficultyAndCreatedAtLessThan(d, to);
    }

    private Map<String, Long> groupQuestionsByType(LocalDateTime from, LocalDateTime to) {
        StringBuilder jpql = new StringBuilder(
                "select q.questionType, count(q) from Question q where 1=1");
        if (from != null) jpql.append(" and q.createdAt >= :from");
        if (to != null)   jpql.append(" and q.createdAt < :to");
        jpql.append(" group by q.questionType");

        var query = em.createQuery(jpql.toString(), Object[].class);
        if (from != null) query.setParameter("from", from);
        if (to != null)   query.setParameter("to", to);

        Map<String, Long> res = new HashMap<>();
        for (Object[] row : query.getResultList()) {
            String type = String.valueOf(row[0]);      // MULTIPLE_CHOICE / ESSAY
            long cnt    = ((Number) row[1]).longValue();
            res.put(type, cnt);
        }
        return res;
    }

    private Map<String, Long> groupQuestionsByLabel(LocalDateTime from, LocalDateTime to) {
        StringBuilder jpql = new StringBuilder(
                "select l, count(q) from Question q join q.labels l where 1=1");
        if (from != null) jpql.append(" and q.createdAt >= :from");
        if (to != null)   jpql.append(" and q.createdAt < :to");
        jpql.append(" group by l");

        var query = em.createQuery(jpql.toString(), Object[].class);
        if (from != null) query.setParameter("from", from);
        if (to != null)   query.setParameter("to", to);

        Map<String, Long> res = new HashMap<>();
        for (Object[] row : query.getResultList()) {
            String label = String.valueOf(row[0]);     // PRACTICE / EXAM
            long cnt     = ((Number) row[1]).longValue();
            res.put(label, cnt);
        }
        return res;
    }

    private Map<Integer, Long> groupQuestionsByChapter(LocalDateTime from, LocalDateTime to) {
        StringBuilder jpql = new StringBuilder(
                "select q.chapter, count(q) from Question q where 1=1");
        if (from != null) jpql.append(" and q.createdAt >= :from");
        if (to != null)   jpql.append(" and q.createdAt < :to");
        jpql.append(" group by q.chapter");

        var query = em.createQuery(jpql.toString(), Object[].class);
        if (from != null) query.setParameter("from", from);
        if (to != null)   query.setParameter("to", to);

        Map<Integer, Long> res = new HashMap<>();
        for (Object[] row : query.getResultList()) {
            Integer chap = (row[0] == null) ? 0 : ((Number) row[0]).intValue();
            long cnt     = ((Number) row[1]).longValue();
            res.put(chap, cnt);
        }
        return res;
    }

    // =====================================================================
    // FileArchive helpers (dùng EntityManager để tránh null-param pitfalls)
    // =====================================================================

    private long countArchiveByStatus(ReviewStatus st, Instant from, Instant to) {
        StringBuilder jpql = new StringBuilder(
                "select count(f) from FileArchive f where f.reviewStatus = :st");
        if (from != null) jpql.append(" and f.createdAt >= :from");
        if (to != null)   jpql.append(" and f.createdAt < :to");

        var q = em.createQuery(jpql.toString(), Long.class)
                .setParameter("st", st);
        if (from != null) q.setParameter("from", from);
        if (to != null)   q.setParameter("to", to);
        return q.getSingleResult();
    }

    private double averageReviewHours(Instant from, Instant to) {
        StringBuilder jpql = new StringBuilder(
                "select f.createdAt, f.reviewedAt from FileArchive f where f.reviewedAt is not null");
        if (from != null) jpql.append(" and f.createdAt >= :from");
        if (to != null)   jpql.append(" and f.createdAt < :to");

        var q = em.createQuery(jpql.toString(), Object[].class);
        if (from != null) q.setParameter("from", from);
        if (to != null)   q.setParameter("to", to);

        List<Object[]> rows = q.getResultList();
        if (rows.isEmpty()) return 0d;

        double sumHours = 0d;
        int n = 0;
        for (Object[] r : rows) {
            Instant created  = (Instant) r[0];
            Instant reviewed = (Instant) r[1];
            if (created != null && reviewed != null) {
                long ms = Duration.between(created, reviewed).toMillis();
                sumHours += (ms / 3600000.0);
                n++;
            }
        }
        return (n == 0) ? 0d : (sumHours / n);
    }
}
