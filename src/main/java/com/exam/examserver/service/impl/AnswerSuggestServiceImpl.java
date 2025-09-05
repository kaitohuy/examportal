package com.exam.examserver.service.impl;

import com.exam.examserver.service.AnswerSuggestService;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AnswerSuggestServiceImpl implements AnswerSuggestService {
    @Override public Optional<String> suggestMcqAnswer(String c, String a, String b, String d, String e) { return Optional.empty(); }
    @Override public Optional<String> suggestEssayAnswer(String c) { return Optional.empty(); }
}
