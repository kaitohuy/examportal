package com.exam.examserver.service;

import com.exam.examserver.dto.exam.QuestionIssueDTO;

public interface QuestionIssueService {
    QuestionIssueDTO flag(Long subjectId, Long questionId, Long reporterUserId, String reason);
    QuestionIssueDTO unflag(Long subjectId, Long questionId, Long actorUserId, boolean actorIsHeadOrAdmin);
}
