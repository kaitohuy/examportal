package com.exam.examserver.repo;

import com.exam.examserver.enums.RoleType;
import com.exam.examserver.model.user.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByRoleName(RoleType roleName);
}
