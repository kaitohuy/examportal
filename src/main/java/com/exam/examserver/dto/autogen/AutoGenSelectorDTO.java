// src/main/java/com/exam/examserver/dto/autogen/AutoGenSelectorDTO.java
package com.exam.examserver.dto.autogen;

import com.exam.examserver.enums.*;
import java.math.BigDecimal;
import java.util.List;

public class AutoGenSelectorDTO {
    public UnitKind unitKind;                 // SUB_ITEM | FULL_QUESTION
    public List<Integer> chapterIn;           // vd: [1,5]
    public BigDecimal pointsEq;               // ưu tiên =; nếu null dùng min/max
    public BigDecimal pointsMin;
    public BigDecimal pointsMax;
    public List<String> typeCodeIn;           // vd: ["KIEU_2_1"]
    public CognitiveLevel cognitive;          // optional
    public RecordStatus status;
    public ItemNature nature;

    public ItemNature getNature() {
        return nature;
    }

    public void setNature(ItemNature nature) {
        this.nature = nature;
    }

    public UnitKind getUnitKind() {
        return unitKind;
    }

    public void setUnitKind(UnitKind unitKind) {
        this.unitKind = unitKind;
    }

    public List<Integer> getChapterIn() {
        return chapterIn;
    }

    public void setChapterIn(List<Integer> chapterIn) {
        this.chapterIn = chapterIn;
    }

    public BigDecimal getPointsEq() {
        return pointsEq;
    }

    public void setPointsEq(BigDecimal pointsEq) {
        this.pointsEq = pointsEq;
    }

    public BigDecimal getPointsMin() {
        return pointsMin;
    }

    public void setPointsMin(BigDecimal pointsMin) {
        this.pointsMin = pointsMin;
    }

    public BigDecimal getPointsMax() {
        return pointsMax;
    }

    public void setPointsMax(BigDecimal pointsMax) {
        this.pointsMax = pointsMax;
    }

    public List<String> getTypeCodeIn() {
        return typeCodeIn;
    }

    public void setTypeCodeIn(List<String> typeCodeIn) {
        this.typeCodeIn = typeCodeIn;
    }

    public CognitiveLevel getCognitive() {
        return cognitive;
    }

    public void setCognitive(CognitiveLevel cognitive) {
        this.cognitive = cognitive;
    }

    public RecordStatus getStatus() {
        return status;
    }

    public void setStatus(RecordStatus status) {
        this.status = status;
    }

    public static AutoGenSelectorDTO chapter(int chapter, BigDecimal pts) {
        AutoGenSelectorDTO sel = new AutoGenSelectorDTO();
        sel.chapterIn = List.of(chapter);
        sel.pointsEq = pts;
        sel.unitKind = UnitKind.SUB_ITEM;
        sel.status = RecordStatus.APPROVED;
        return sel;
    }

    public static AutoGenSelectorDTO bundleOfChapter(int chapter, BigDecimal pts) {
        AutoGenSelectorDTO sel = new AutoGenSelectorDTO();
        sel.chapterIn = List.of(chapter);
        sel.pointsEq = pts;
        sel.unitKind = UnitKind.FULL_QUESTION;
        sel.status = RecordStatus.APPROVED;
        return sel;
    }
}
