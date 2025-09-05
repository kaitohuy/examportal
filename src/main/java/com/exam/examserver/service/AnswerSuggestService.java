package com.exam.examserver.service;

import java.util.Optional;

public interface AnswerSuggestService {
    Optional<String> suggestMcqAnswer(String content, String a, String b, String c, String d);
    Optional<String> suggestEssayAnswer(String content);
}
