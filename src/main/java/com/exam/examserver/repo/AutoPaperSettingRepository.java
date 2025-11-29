// src/main/java/com/exam/examserver/repo/auto/AutoPaperSettingRepository.java
package com.exam.examserver.repo;

import com.exam.examserver.enums.AutoSettingKind;
import com.exam.examserver.model.exam.AutoPaperSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AutoPaperSettingRepository extends JpaRepository<AutoPaperSetting, Long> {
    Optional<AutoPaperSetting> findBySubjectId(Long subjectId);
    boolean existsBySubjectId(Long subjectId);

    Optional<AutoPaperSetting> findBySubjectIdAndKind(Long subjectId, AutoSettingKind kind);
    boolean existsBySubjectIdAndKind(Long subjectId, AutoSettingKind kind);
}
