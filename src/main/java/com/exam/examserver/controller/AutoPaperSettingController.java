// src/main/java/com/exam/examserver/controller/auto/AutoPaperSettingController.java
package com.exam.examserver.controller;

import com.exam.examserver.dto.autogen.AutoPaperSettingDTO;
import com.exam.examserver.service.QuestionMetaService;
import com.exam.examserver.service.auto.AutoPaperSettingService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/auto-paper/setting")
@CrossOrigin("*")
public class AutoPaperSettingController {

    private final AutoPaperSettingService settingService;
    private final QuestionMetaService metaService;

    public AutoPaperSettingController(AutoPaperSettingService settingService,
                                      QuestionMetaService metaService) {
        this.settingService = settingService;
        this.metaService = metaService;
    }

    /** GET: Lấy cấu hình hiện tại (tự tạo mặc định nếu chưa có) */
    @GetMapping("/{subjectId}")
    public AutoPaperSettingDTO get(@PathVariable Long subjectId, Principal p) {
        String user = (p != null ? p.getName() : "system");
        return settingService.getOrCreateDefault(subjectId, user);
    }

    /** PUT: Cập nhật cấu hình */
    @PutMapping("/{subjectId}")
    public AutoPaperSettingDTO update(@PathVariable Long subjectId,
                                      @RequestBody AutoPaperSettingDTO dto) {
        return settingService.upsertBySubjectId(subjectId, dto);
    }

    /** GET: Trả về danh sách typeCode distinct (lọc theo môn học) */
    @GetMapping("/{subjectId}/type-codes")
    public List<String> listTypeCodes(@PathVariable Long subjectId) {
        return metaService.findDistinctTypeCodesApproved(subjectId);
    }

    /** POST: Reset cấu hình về mặc định (ghi đè nếu có sẵn) */
    @PostMapping("/{subjectId}/reset-default")
    public AutoPaperSettingDTO resetDefault(@PathVariable Long subjectId, Principal p) {
        String user = (p != null ? p.getName() : "system");
        return settingService.resetToDefault(subjectId, user);
    }
}
