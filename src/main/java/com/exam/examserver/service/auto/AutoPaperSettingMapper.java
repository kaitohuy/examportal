// src/main/java/com/exam/examserver/service/auto/AutoPaperSettingMapper.java
package com.exam.examserver.service.auto;

import com.exam.examserver.dto.autogen.AutoPaperSettingDTO;
import com.exam.examserver.enums.AutoSettingKind;
import com.exam.examserver.model.exam.AutoPaperSetting;

public final class AutoPaperSettingMapper {
    private AutoPaperSettingMapper() {}

    public static AutoPaperSettingDTO toDTO(AutoPaperSetting e) {
        AutoPaperSettingDTO d = new AutoPaperSettingDTO();
        d.id = e.getId();
        d.subjectId = e.getSubjectId();
        d.name = e.getName();
        d.variants = e.getVariants();
        d.labelScope = e.getLabelScope();
        d.noRepeatWithin = e.getNoRepeatWithin();
        d.noRepeatAcross = e.getNoRepeatAcross();
        d.notUsedYears = e.getNotUsedYears();
        d.steps = e.getSteps();
        d.kind = e.getKind();
        return d;
    }

    public static void applyDTO(AutoPaperSetting e, AutoPaperSettingDTO d) {
        e.setName(d.name);
        e.setVariants(d.variants <= 0 ? 1 : d.variants);
        e.setLabelScope(d.labelScope);
        e.setNoRepeatWithin(d.noRepeatWithin);
        e.setNoRepeatAcross(d.noRepeatAcross);
        e.setNotUsedYears(d.notUsedYears == null ? 0 : d.notUsedYears);
        e.setSteps(d.steps);
        e.setKind(d.kind == null ? AutoSettingKind.EXAM : d.kind);
    }
}
