package com.exam.examserver.repo;

import com.exam.examserver.model.exam.Subject;
import com.exam.examserver.model.user.TeacherSubject;
import com.exam.examserver.model.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeacherSubjectRepository extends JpaRepository<TeacherSubject, Long> {
    boolean existsBySubjectAndTeacher(Subject subject, User teacher);
    List<TeacherSubject> findByTeacherId(Long teacherId);
    List<TeacherSubject> findBySubjectId(Long subjectId);
    Optional<TeacherSubject> findBySubjectIdAndTeacherId(Long subjectId, Long teacherId);

}
