// src/main/java/com/exam/examserver/controller/auto/AutoPaperSettingController.java
package com.exam.examserver.controller;

import com.exam.examserver.dto.autogen.AutoPaperSettingDTO;
import com.exam.examserver.enums.AutoSettingKind;
import com.exam.examserver.service.QuestionMetaService;
import com.exam.examserver.service.auto.AutoPaperSettingService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/auto-paper/setting")
@CrossOrigin("*")
public class AutoPaperSettingController {

    private final AutoPaperSettingService settingService;
    private final QuestionMetaService metaService;

    public AutoPaperSettingController(AutoPaperSettingService settingService, QuestionMetaService metaService) {
        this.settingService = settingService;
        this.metaService = metaService;
    }

    // GET: lấy theo kind (mặc định EXAM)
    @GetMapping("/{subjectId}")
    public AutoPaperSettingDTO get(@PathVariable Long subjectId,
                                   @RequestParam(name="kind", defaultValue="EXAM") AutoSettingKind kind,
                                   Principal p) {
        String user = (p != null ? p.getName() : "system");
        return settingService.getOrCreateDefault(subjectId, kind, user);
    }

    // PUT: cập nhật theo kind, chặn quyền theo kind
    @PutMapping("/{subjectId}")
    public AutoPaperSettingDTO update(@PathVariable Long subjectId,
                                      @RequestParam(name="kind", defaultValue="EXAM") AutoSettingKind kind,
                                      @RequestBody AutoPaperSettingDTO dto,
                                      Authentication auth) {
        guard(kind, auth, /*write*/true);
        dto.kind = kind;
        return settingService.upsertBySubjectId(subjectId, dto);
    }

    // RESET default theo kind
    @PostMapping("/{subjectId}/reset-default")
    public AutoPaperSettingDTO resetDefault(@PathVariable Long subjectId,
                                            @RequestParam(name="kind", defaultValue="EXAM") AutoSettingKind kind,
                                            Principal p,
                                            Authentication auth) {
        guard(kind, auth, /*write*/true);
        String user = (p != null ? p.getName() : "system");
        return settingService.resetToDefault(subjectId, kind, user);
    }

    // giữ nguyên type-codes
    @GetMapping("/{subjectId}/type-codes")
    public List<String> listTypeCodes(@PathVariable Long subjectId) {
        return metaService.findDistinctTypeCodesApproved(subjectId);
    }

    private static void guard(AutoSettingKind kind, Authentication auth, boolean write) {
        var roles = auth == null ? Set.<String>of() :
                auth.getAuthorities().stream().map(a->a.getAuthority()).collect(java.util.stream.Collectors.toSet());
        boolean isHeadOrAdmin = roles.contains("HEAD") || roles.contains("ADMIN");
        boolean isTeacher = roles.contains("TEACHER");
        if (kind == AutoSettingKind.EXAM) {
            if (!isHeadOrAdmin) throw new AccessDeniedException("Forbidden");
        } else { // PRACTICE
            if (!(isTeacher || isHeadOrAdmin)) throw new AccessDeniedException("Forbidden");
        }
    }
}
