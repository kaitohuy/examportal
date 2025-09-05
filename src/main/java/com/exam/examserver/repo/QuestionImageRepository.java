package com.exam.examserver.repo;

import com.exam.examserver.model.exam.QuestionImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionImageRepository extends JpaRepository<QuestionImage, Long> {
    List<QuestionImage> findByQuestionIdOrderByOrderIndexAsc(Long questionId);
    void deleteByQuestionId(Long questionId);
}
