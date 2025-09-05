package com.exam.examserver.dto.importing;

import java.time.Instant;

public record FileArchiveDTO(
        Long id, String filename, String mimeType, long sizeBytes, String kind,
        Long subjectId, Long userId, Instant createdAt,
        String uploaderName, String subjectName,
        String variant, String reviewStatus, String reviewNote, Instant reviewDeadline,
        Instant reviewedAt, Long reviewedById, String reviewedByName
) {}

