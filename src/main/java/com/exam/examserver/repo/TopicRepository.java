package com.exam.examserver.repo;

import com.exam.examserver.model.exam.Topic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TopicRepository extends JpaRepository<Topic, Long> {
    Optional<Topic> findBySubjectIdAndCode(Long subjectId, String code);
}
