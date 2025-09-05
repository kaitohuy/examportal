package com.exam.examserver.dto.importing;

import java.util.List;
import java.util.Map;

public record ExportSubmitRequest(
        Long subjectId,
        List<Long> questionIds,
        Map<String, Object> options // chứa format, includeAnswers, variant, vv.
) {}
