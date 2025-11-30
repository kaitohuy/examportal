package com.exam.examserver.repo;

import com.exam.examserver.enums.RoleType;
import com.exam.examserver.enums.Status;
import com.exam.examserver.model.user.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = {"userRoles", "userRoles.role"})
    Optional<User> findByUsername(String username);

    long countByStatus(Status status);
    long countByUserRoles_Role_RoleName(RoleType roleName);

    List<User> findByStatus(Status status);
    Optional<User> findByEmailIgnoreCase(String email);
    @Query("""
       select u.id
       from User u
       where lower(u.username) like lower(concat('%', :q, '%'))
          or lower(concat(coalesce(u.firstName,''),' ',coalesce(u.lastName,'')))
                like lower(concat('%', :q, '%'))
          or lower(concat(coalesce(u.lastName,''),' ',coalesce(u.firstName,'')))
                like lower(concat('%', :q, '%'))
       """)
    List<Long> searchIdsByKeyword(@Param("q") String q);

    @Query("""
      select count(distinct u.id)
      from User u
      join u.teacherSubjects ts
      join ts.subject s
      where s.department.id = :deptId
    """)
    long countByDepartment_Id(@Param("deptId") Long deptId);


    @Query("""
      select count(distinct u.id)
      from User u
      join u.userRoles ur
      join ur.role r
      join u.teacherSubjects ts
      join ts.subject s
      where r.roleName = :role
        and s.department.id = :deptId
    """)
    long countByRoleAndDepartment(@Param("role") RoleType role,
                                  @Param("deptId") Long deptId);

    @Query("""
  select distinct u
  from User u
  join u.userRoles ur
  join ur.role r
  left join u.department d
  where r.roleName = :role
    and (:deptId is null or d.id = :deptId)
""")
    List<User> findByRoleAndDepartmentId(@Param("role") RoleType role,
                                         @Param("deptId") Long deptId);

    @Query("""
  select distinct u
  from User u
  join u.userRoles ur
  join ur.role r
  where r.roleName = :role
""")
    List<User> findByRole(@Param("role") RoleType role);

    @Query("""
  select distinct u
  from User u
  where not exists (
    select 1
    from UserRole ur
    join ur.role r
    where ur.user = u
      and r.roleName = :role
  )
""")
    List<User> findUsersWithoutRole(@Param("role") RoleType role);
}
