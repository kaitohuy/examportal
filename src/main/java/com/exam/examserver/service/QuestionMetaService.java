// src/main/java/com/exam/examserver/service/meta/QuestionMetaService.java
package com.exam.examserver.service;

import com.exam.examserver.enums.CognitiveLevel;
import com.exam.examserver.enums.ItemNature;
import com.exam.examserver.enums.RecordStatus;
import com.exam.examserver.enums.UnitKind;
import com.exam.examserver.model.exam.QuestionMeta;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface QuestionMetaService {
    QuestionMeta upsertDefault(Long questionId,
                               UnitKind unitKind,
                               BigDecimal points,
                               Integer chapter,
                               Long topicId,
                               RecordStatus status,
                               CognitiveLevel cognitiveLevel,
                               String typeCode,
                               ItemNature itemNature,
                               String problemType);

    // tạm giữ hàm cũ — gọi sang hàm mới với UNKNOWN
    default QuestionMeta upsertDefault(Long questionId,
                                       UnitKind unitKind,
                                       BigDecimal points,
                                       Integer chapter,
                                       Long topicId,
                                       RecordStatus status,
                                       CognitiveLevel cognitiveLevel,
                                       String typeCode,
                                       String problemType) {
        return upsertDefault(questionId, unitKind, points, chapter, topicId, status, cognitiveLevel, typeCode, ItemNature.UNKNOWN, problemType);
    }

    void markUsed(Collection<Long> questionIds);

    List<String> findDistinctTypeCodesApproved(Long subjectId);

    List<String> getProblemTypesBySubject(Long subjectId);
}
