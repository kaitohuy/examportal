// src/main/java/com/exam/examserver/service/auto/AutoPaperSettingService.java
package com.exam.examserver.service.auto;

import com.exam.examserver.dto.autogen.AutoPaperSettingDTO;
import com.exam.examserver.enums.AutoSettingKind;
import com.exam.examserver.model.exam.AutoPaperSetting;
import com.exam.examserver.repo.AutoPaperSettingRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AutoPaperSettingService {
    private final AutoPaperSettingRepository repo;

    public AutoPaperSettingService(AutoPaperSettingRepository repo) {
        this.repo = repo;
    }

    /* === find === */
    public Optional<AutoPaperSetting> findBySubject(Long subjectId) {
        // legacy: EXAM
        return repo.findBySubjectIdAndKind(subjectId, AutoSettingKind.EXAM)
                .or(() -> repo.findBySubjectId(subjectId)); // fallback
    }
    public Optional<AutoPaperSetting> findBySubject(Long subjectId, AutoSettingKind kind) {
        return repo.findBySubjectIdAndKind(subjectId, kind);
    }

    /* === default builders === */
    private AutoPaperSettingDTO buildDefaultDTO(AutoSettingKind kind) {
        AutoPaperSettingDTO dto = new AutoPaperSettingDTO();
        dto.kind = (kind == null ? AutoSettingKind.EXAM : kind);
        dto.name = (dto.kind == AutoSettingKind.EXAM ? "Default (Exam)" : "Default (Practice)");
        dto.variants = (dto.kind == AutoSettingKind.EXAM ? 1 : 1);
        dto.noRepeatWithin = true;
        dto.noRepeatAcross = (dto.kind == AutoSettingKind.EXAM);
        dto.notUsedYears = 1;
        dto.labelScope = null; // cho phép FE set PRACTICE sau
        dto.steps = (dto.kind == AutoSettingKind.EXAM)
                ? AutoPaperSettingDefaults.defaultExamSteps()
                : AutoPaperSettingDefaults.defaultPracticeSteps();
        return dto;
    }

    @Transactional
    public AutoPaperSettingDTO getOrCreateDefault(Long subjectId, String username) {
        // legacy (EXAM)
        return getOrCreateDefault(subjectId, AutoSettingKind.EXAM, username);
    }

    @Transactional
    public AutoPaperSettingDTO getOrCreateDefault(Long subjectId,
                                                  AutoSettingKind kind,
                                                  String username) {
        var opt = repo.findBySubjectIdAndKind(subjectId, kind);
        if (opt.isPresent()) return AutoPaperSettingMapper.toDTO(opt.get());

        AutoPaperSetting e = new AutoPaperSetting();
        e.setSubjectId(subjectId);
        e.setKind(kind);
        AutoPaperSettingMapper.applyDTO(e, buildDefaultDTO(kind));
        e.setCreatedBy(username == null ? "system" : username);
        e = repo.save(e);
        return AutoPaperSettingMapper.toDTO(e);
    }

    @Transactional
    public AutoPaperSettingDTO upsertBySubjectId(Long subjectId, AutoPaperSettingDTO dto) {
        if (dto == null) throw new IllegalArgumentException("Body is required");
        if (dto.steps == null || dto.steps.isEmpty()) throw new IllegalArgumentException("steps is required");
        AutoSettingKind kind = (dto.kind == null ? AutoSettingKind.EXAM : dto.kind);

        AutoPaperSetting e = repo.findBySubjectIdAndKind(subjectId, kind).orElseGet(() -> {
            AutoPaperSetting n = new AutoPaperSetting();
            n.setSubjectId(subjectId);
            n.setKind(kind);
            n.setName(dto.name == null || dto.name.isBlank() ? (kind==AutoSettingKind.EXAM?"Default (Exam)":"Default (Practice)") : dto.name);
            n.setCreatedBy("system");
            return n;
        });

        dto.subjectId = subjectId;
        dto.kind = kind;
        AutoPaperSettingMapper.applyDTO(e, dto);
        e = repo.save(e);
        return AutoPaperSettingMapper.toDTO(e);
    }

    @Transactional
    public AutoPaperSettingDTO resetToDefault(Long subjectId, String createdBy) {
        return resetToDefault(subjectId, AutoSettingKind.EXAM, createdBy);
    }
    @Transactional
    public AutoPaperSettingDTO resetToDefault(Long subjectId, AutoSettingKind kind, String createdBy) {
        AutoPaperSettingDTO def = buildDefaultDTO(kind);
        def.subjectId = subjectId;
        return upsertBySubjectId(subjectId, def);
    }
}
