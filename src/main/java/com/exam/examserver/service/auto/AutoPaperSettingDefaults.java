// src/main/java/com/exam/examserver/service/auto/AutoPaperSettingDefaults.java
package com.exam.examserver.service.auto;

import com.exam.examserver.dto.autogen.AutoGenSelectorDTO;
import com.exam.examserver.dto.autogen.AutoGenStepDTO;
import com.exam.examserver.enums.ItemNature;
import com.exam.examserver.enums.RecordStatus;
import com.exam.examserver.enums.UnitKind;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class AutoPaperSettingDefaults {
    private AutoPaperSettingDefaults() {}

    public static List<AutoGenStepDTO> defaultSteps() {
        List<AutoGenStepDTO> defaults = new ArrayList<>();

        // Câu 1: 02 ý — 1đ Ch1 + 1đ Ch5
        {
            AutoGenStepDTO s = new AutoGenStepDTO();
            s.title = "Câu 1: 1đ Ch.1 + 1đ Ch.5";
            s.selectors = List.of(
                    AutoGenSelectorDTO.chapter(1, new BigDecimal("1.00")),
                    AutoGenSelectorDTO.chapter(5, new BigDecimal("1.00"))
            );
            defaults.add(s);
        }

        // Câu 2: bundle typeCode 2.1
        {
            AutoGenSelectorDTO sel = new AutoGenSelectorDTO();
            sel.unitKind  = UnitKind.FULL_QUESTION;
            sel.chapterIn = List.of(2);
            sel.pointsEq  = new BigDecimal("2.00");
            sel.typeCodeIn = List.of("2.1");
            sel.status    = RecordStatus.APPROVED;
            AutoGenStepDTO s = new AutoGenStepDTO();
            s.title = "Câu 2: 2đ kiểu 2.1";
            s.selectors = List.of(sel);
            defaults.add(s);
        }

        // Câu 3: bundle typeCode 2.2
        {
            AutoGenSelectorDTO sel = new AutoGenSelectorDTO();
            sel.unitKind  = UnitKind.FULL_QUESTION;
            sel.chapterIn = List.of(2);
            sel.pointsEq  = new BigDecimal("2.00");
            sel.typeCodeIn = List.of("2.2");
            sel.status    = RecordStatus.APPROVED;
            AutoGenStepDTO s = new AutoGenStepDTO();
            s.title = "Câu 3: 2đ kiểu 2.2";
            s.selectors = List.of(sel);
            defaults.add(s);
        }

        // Câu 4: 2 ý trong chương 3 — THEORY + EXERCISE
        {
            AutoGenSelectorDTO a = AutoGenSelectorDTO.chapter(3, new BigDecimal("1.00"));
            a.nature = ItemNature.THEORY;
            AutoGenSelectorDTO b = AutoGenSelectorDTO.chapter(3, new BigDecimal("1.00"));
            b.nature = ItemNature.EXERCISE;
            AutoGenStepDTO s = new AutoGenStepDTO();
            s.title = "Câu 4: 1đ Lý thuyết + 1đ Ứng dụng (Ch.3)";
            s.selectors = List.of(a, b);
            defaults.add(s);
        }

        // Câu 5: bundle theo chương 4
        {
            AutoGenSelectorDTO sel = new AutoGenSelectorDTO();
            sel.unitKind  = UnitKind.FULL_QUESTION;
            sel.chapterIn = List.of(4);
            sel.pointsEq  = new BigDecimal("2.00");
            sel.status    = RecordStatus.APPROVED;
            AutoGenStepDTO s = new AutoGenStepDTO();
            s.title = "Câu 5: 2đ Ch.4";
            s.selectors = List.of(sel);
            defaults.add(s);
        }

        return defaults;
    }
}
