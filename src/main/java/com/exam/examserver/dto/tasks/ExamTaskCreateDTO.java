package com.exam.examserver.dto.tasks;

import java.time.Instant;

public record ExamTaskCreateDTO(
        Long subjectId,
        Long assignedToId,
        String title,
        String instructions,
        String structureJson,  // JSON string từ FE
        Instant dueAt
) {}

