package com.exam.examserver.config;

import com.exam.examserver.enums.RoleType;
import com.exam.examserver.model.user.User;
import com.exam.examserver.model.user.UserRole;
import com.exam.examserver.repo.DepartmentRepository;
import com.exam.examserver.repo.SubjectRepository;
import com.exam.examserver.repo.UserRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Component
public class ScopeResolver {

    private final UserRepository userRepo;
    private final DepartmentRepository deptRepo;
    private final SubjectRepository subjectRepo;

    public ScopeResolver(UserRepository userRepo, DepartmentRepository deptRepo, SubjectRepository subjectRepo) {
        this.userRepo = userRepo;
        this.deptRepo = deptRepo;
        this.subjectRepo = subjectRepo;
    }

    public static final class Scope {
        public final boolean all;            // ADMIN
        public final Long onlyUserId;        // TEACHER (user ID)
        public final List<Long> subjectIds;  // HEAD hoặc TEACHER (subjects có quyền truy cập)

        private Scope(boolean all, Long onlyUserId, List<Long> subjectIds) {
            this.all = all;
            this.onlyUserId = onlyUserId;
            this.subjectIds = subjectIds;
        }

        public static Scope all() {
            return new Scope(true, null, null);
        }

        public static Scope onlyUser(Long uid) {
            return new Scope(false, uid, null);
        }

        public static Scope bySubjects(List<Long> s) {
            return new Scope(false, null, s);
        }

        // NEW: TEACHER với cả userId và subjectIds
        public static Scope teacherWithSubjects(Long uid, List<Long> sids) {
            return new Scope(false, uid, sids);
        }
    }

    /** Resolve scope dựa vào username trong SecurityContext. */
    public Scope resolveByUsername(String username) {
        if (username == null || username.isBlank()) return Scope.onlyUser(-1L);

        Optional<User> ou = userRepo.findByUsername(username);
        if (ou.isEmpty()) return Scope.onlyUser(-1L);

        User u = ou.get();
        Long uid = u.getId();
        Set<UserRole> urs = u.getUserRoles();

        // ADMIN: full access
        boolean isAdmin = urs.stream().anyMatch(ur -> ur.getRole().getRoleName() == RoleType.ADMIN);
        if (isAdmin) return Scope.all();

        // HEAD: quản lý department
        boolean isHead = urs.stream().anyMatch(ur -> ur.getRole().getRoleName() == RoleType.HEAD);
        if (isHead) {
            var deptOpt = deptRepo.findByHeadUser_Id(uid);
            if (deptOpt.isEmpty()) return Scope.bySubjects(List.of());

            Long deptId = deptOpt.get().getId();
            var subjects = subjectRepo.findByDepartmentId(deptId);
            var sids = subjects.stream().map(s -> s.getId()).filter(Objects::nonNull).toList();
            return Scope.bySubjects(sids);
        }

        // TEACHER: trả về userId + danh sách subject được assign
        var teacherSubjects = subjectRepo.findByTeacherIdWithTeachers(uid);
        var sids = teacherSubjects.stream().map(s -> s.getId()).filter(Objects::nonNull).toList();
        return Scope.teacherWithSubjects(uid, sids);
    }
}