package com.exam.examserver.repo;

import com.exam.examserver.model.user.Department;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    Optional<Department> findByName(String name);
    boolean existsByName(String name);
    List<Department> findByHeadUserId(Long headUserId);
    Optional<Department> findByHeadUser_Id(Long userId);
    @Query("select count(d) from Department d where d.headUser is null")
    long countWithoutHead();

}

