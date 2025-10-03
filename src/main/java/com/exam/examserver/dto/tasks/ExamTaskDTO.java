package com.exam.examserver.dto.tasks;

import com.exam.examserver.enums.ExamTaskStatus;
import com.exam.examserver.model.exam.ExamTask;

import java.time.Instant;

public record ExamTaskDTO(
        Long id,
        Long subjectId,
        Long headDepartmentId,
        Long assignedToId,
        Long createdByHeadId,
        String title,
        String instructions,
        String structureJson,
        ExamTaskStatus status,
        Instant dueAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt,

        // tên hiển thị (đỡ FE phải gọi thêm)
        String subjectName,
        String assignedToName,
        String createdByName
) {
    public static ExamTaskDTO of(ExamTask t, String subjectName, String assignedName, String createdName) {
        return new ExamTaskDTO(
                t.getId(), t.getSubjectId(), t.getHeadDepartmentId(),
                t.getAssignedToId(), t.getCreatedByHeadId(),
                t.getTitle(), t.getInstructions(), t.getStructureJson(),
                t.getStatus(), t.getDueAt(), t.getCompletedAt(),
                t.getCreatedAt(), t.getUpdatedAt(),
                subjectName, assignedName, createdName
        );
    }
}
