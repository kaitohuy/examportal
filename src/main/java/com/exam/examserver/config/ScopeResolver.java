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
        public final Long onlyUserId;        // TEACHER
        public final List<Long> subjectIds;  // HEAD
        private Scope(boolean all, Long onlyUserId, List<Long> subjectIds) {
            this.all = all; this.onlyUserId = onlyUserId; this.subjectIds = subjectIds;
        }
        public static Scope all()                   { return new Scope(true, null, null); }
        public static Scope onlyUser(Long uid)      { return new Scope(false, uid, null); }
        public static Scope bySubjects(List<Long> s){ return new Scope(false, null, s); }
    }

    /** Resolve scope dựa vào username trong SecurityContext. */
    public Scope resolveByUsername(String username) {
        if (username == null || username.isBlank()) return Scope.onlyUser(-1L);

        Optional<User> ou = userRepo.findByUsername(username); // EntityGraph fetch userRoles + role
        if (ou.isEmpty()) return Scope.onlyUser(-1L);

        User u = ou.get();
        Long uid = u.getId();
        Set<UserRole> urs = u.getUserRoles();

        boolean isAdmin = urs.stream().anyMatch(ur -> ur.getRole().getRoleName() == RoleType.ADMIN);
        if (isAdmin) return Scope.all();

        boolean isHead = urs.stream().anyMatch(ur -> ur.getRole().getRoleName() == RoleType.HEAD);
        if (isHead) {
            // HEAD quản lý đúng 1 Department thông qua Department.headUser
            var deptOpt = deptRepo.findByHeadUser_Id(uid);
            if (deptOpt.isEmpty()) return Scope.bySubjects(List.of()); // không quản lý gì
            Long deptId = deptOpt.get().getId();

            // Lấy tất cả Subject thuộc department này → subjectIds
            var subjects = subjectRepo.findByDepartmentId(deptId);
            var sids = subjects.stream().map(s -> s.getId()).filter(Objects::nonNull).toList();
            return Scope.bySubjects(sids);
        }

        // mặc định TEACHER
        return Scope.onlyUser(uid);
    }
}
