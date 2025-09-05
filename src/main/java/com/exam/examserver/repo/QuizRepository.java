package com.exam.examserver.repo;

import com.exam.examserver.model.exam.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuizRepository extends JpaRepository<Quiz, Long> {


    List<Quiz> findBySubjectId(Long subjectId);
}
