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
}
