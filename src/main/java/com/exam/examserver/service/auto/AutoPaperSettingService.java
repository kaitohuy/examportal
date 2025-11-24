// src/main/java/com/exam/examserver/service/auto/AutoPaperSettingService.java
package com.exam.examserver.service.auto;

import com.exam.examserver.dto.autogen.AutoPaperSettingDTO;
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

    /** Dùng cho AutoPaperService */
    public Optional<AutoPaperSetting> findBySubject(Long subjectId) {
        return repo.findBySubjectId(subjectId);
    }

    /** Lấy hoặc tạo bản mặc định nếu chưa có */
    @Transactional
    public AutoPaperSettingDTO getOrCreateDefault(Long subjectId, String username) {
        var opt = repo.findBySubjectId(subjectId);
        if (opt.isPresent()) return AutoPaperSettingMapper.toDTO(opt.get());

        AutoPaperSetting e = new AutoPaperSetting();
        e.setSubjectId(subjectId);
        e.setName("Default");
        e.setVariants(1);
        e.setNoRepeatWithin(true);
        e.setNoRepeatAcross(false);
        e.setNotUsedYears(1);
        e.setSteps(AutoPaperSettingDefaults.defaultSteps());
        e.setCreatedBy(username == null ? "system" : username);

        e = repo.save(e);
        return AutoPaperSettingMapper.toDTO(e);
    }

    /** Cập nhật hoặc tạo mới theo subjectId */
    @Transactional
    public AutoPaperSettingDTO upsertBySubjectId(Long subjectId, AutoPaperSettingDTO dto) {
        if (dto == null) throw new IllegalArgumentException("Body is required");
        if (dto.steps == null || dto.steps.isEmpty())
            throw new IllegalArgumentException("steps is required");

        AutoPaperSetting e = repo.findBySubjectId(subjectId).orElseGet(() -> {
            AutoPaperSetting n = new AutoPaperSetting();
            n.setSubjectId(subjectId);
            n.setName(dto.name == null || dto.name.isBlank() ? "Default" : dto.name);
            n.setCreatedBy("system");
            return n;
        });

        dto.subjectId = subjectId;
        AutoPaperSettingMapper.applyDTO(e, dto);
        e = repo.save(e);
        return AutoPaperSettingMapper.toDTO(e);
    }

    public AutoPaperSetting findRequired(Long subjectId) {
        return repo.findBySubjectId(subjectId)
                .orElseThrow(() -> new EntityNotFoundException("Setting not found for subject " + subjectId));
    }

    /** Dựng 1 DTO default thuần (dùng lại ở resetDefault) */
    private AutoPaperSettingDTO buildDefaultDTO() {
        AutoPaperSettingDTO dto = new AutoPaperSettingDTO();
        dto.name = "Default";
        dto.variants = 1;
        dto.noRepeatWithin = true;
        dto.noRepeatAcross = false;
        dto.notUsedYears = 1;
        dto.labelScope = null; // không lọc nhãn
        dto.steps = AutoPaperSettingDefaults.defaultSteps();
        return dto;
    }

    /** Ghi đè/đặt lại cấu hình về mặc định */
    @Transactional
    public AutoPaperSettingDTO resetToDefault(Long subjectId, String createdBy) {
        AutoPaperSettingDTO def = buildDefaultDTO();
        def.subjectId = subjectId;
        return upsertBySubjectId(subjectId, def);
    }
}
