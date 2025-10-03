package com.exam.examserver.dto.tasks;

import com.exam.examserver.enums.ExamTaskStatus;

public record ExamTaskUpdateStatusDTO(
        ExamTaskStatus status   // IN_PROGRESS | DONE | CANCELLED
) {}
