package com.exam.examserver.repo;

import com.exam.examserver.model.exam.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubjectRepository extends JpaRepository<Subject, Long> {
    List<Subject> findByDepartmentId(Long departmentId);

    // Query fetch dữ liệu giáo viên
    @Query("SELECT s FROM Subject s " +
            "LEFT JOIN FETCH s.teacherSubjects ts " +
            "LEFT JOIN FETCH ts.teacher " +
            "WHERE s.id = :id")
    Optional<Subject> findByIdWithTeachers(@Param("id") Long id);

    boolean existsByCode(String code);

    @Query("""
           select s.id from Subject s
           where lower(s.name) like lower(concat('%', :q, '%'))
              or lower(s.code) like lower(concat('%', :q, '%'))
           """)
    List<Long> searchIdsByKeyword(@Param("q") String q);
}

