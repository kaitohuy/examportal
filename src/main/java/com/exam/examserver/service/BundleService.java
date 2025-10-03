// src/main/java/com/exam/examserver/service/bundle/BundleService.java
package com.exam.examserver.service;

import com.exam.examserver.enums.RecordStatus;
import com.exam.examserver.model.exam.QuestionBundle;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface BundleService {

    record CreateItem(Long questionId, Integer orderIndex, BigDecimal pointsOverride, String note) {}

    QuestionBundle create(Long subjectId,
                          Long createdByUserId,
                          String title,
                          String instructions,
                          List<CreateItem> items,
                          BigDecimal totalPoints,
                          RecordStatus status);

    Map<Long, String> findInstructionsByQuestionIds(Collection<Long> questionIds);
}
