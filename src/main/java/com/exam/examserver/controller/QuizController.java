package com.exam.examserver.controller;

import com.exam.examserver.dto.exam.*;
import com.exam.examserver.model.user.CustomUserDetails;
import com.exam.examserver.service.import_export.QuizExportService;
import com.exam.examserver.service.QuizService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/subject/{subjectId}/quizzes")
@CrossOrigin("*")
public class QuizController {

    private final QuizService quizService;
    private final QuizExportService quizExportService;

    public QuizController(QuizService quizService, QuizExportService quizExportService) {
        this.quizService = quizService;
        this.quizExportService = quizExportService;
    }

    @GetMapping
    public List<QuizDTO> listQuizzes(@PathVariable("subjectId") Long subjectId) {
        return quizService.getAllBySubject(subjectId);
    }

    @GetMapping("/{quizId}")
    public ResponseEntity<QuizDTO> getQuiz(@PathVariable("subjectId") Long subjectId,
                                           @PathVariable("quizId") Long quizId) {
        QuizDTO dto = quizService.getById(quizId, subjectId); // Truyền subjectId vào service
        return ResponseEntity.ok(dto);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuizDTO createQuiz(@PathVariable("subjectId") Long subjectId,
                              @RequestBody CreateQuizDTO payload,
                              @AuthenticationPrincipal CustomUserDetails me) {
        return quizService.create(subjectId, payload, me.getId());
    }

    @PutMapping("/{quizId}")
    public QuizDTO updateQuiz(@PathVariable("subjectId") Long subjectId,
                              @PathVariable("quizId") Long quizId,
                              @RequestBody CreateQuizDTO payload) {
        return quizService.update(quizId, subjectId, payload); // subjectId vào service
    }

    @PutMapping("/{quizId}/questions")
    public QuizDTO updateQuizQuestions(
            @PathVariable("subjectId") Long subjectId,
            @PathVariable("quizId") Long quizId,
            @RequestBody List<AddQuizQuestionDTO> questions,
            @AuthenticationPrincipal CustomUserDetails me) {
        return quizService.updateQuestions(quizId, subjectId, questions, me.getId());
    }

    @DeleteMapping("/{quizId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteQuiz(@PathVariable("subjectId") Long subjectId,
                           @PathVariable("quizId") Long quizId) {
        quizService.delete(quizId);
    }

    @GetMapping("/{quizId}/questions")
    public List<QuestionDTO> listQuizQuestions(@PathVariable("subjectId") Long subjectId,
                                               @PathVariable("quizId") Long quizId,
                                               Authentication auth) {
        boolean isHead = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ADMIN") || a.getAuthority().equals("HEAD"));
        boolean hideAnswer = !isHead;
        return quizService.getQuestionsByQuiz(quizId, hideAnswer);
    }

    @PostMapping("/{quizId}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public List<QuizQuestionDTO> addQuestionsToQuiz(
            @PathVariable("subjectId") Long subjectId,
            @PathVariable("quizId") Long quizId,
            @RequestBody AddQuizQuestionsDTO payload,
            @AuthenticationPrincipal CustomUserDetails me) {
        return quizService.addQuestionsToQuiz(quizId, subjectId, payload, me.getId());
    }

    @DeleteMapping("/{quizId}/questions/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeQuestionFromQuiz(
            @PathVariable("subjectId") Long subjectId,
            @PathVariable("quizId") Long quizId,
            @PathVariable("questionId") Long questionId) {
        quizService.removeQuestionFromQuiz(quizId, questionId);
    }

    @GetMapping("/{quizId}/export")
    public ResponseEntity<byte[]> exportQuizToPdf(
            @PathVariable("subjectId") Long subjectId,
            @PathVariable("quizId") Long quizId,
            @RequestParam(value = "includeAnswers", defaultValue = "false") boolean includeAnswers,
            @RequestParam(value = "format", defaultValue = "pdf") String format,
            Authentication auth) {
        // Kiểm tra quyền
        boolean isHead = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ADMIN") || a.getAuthority().equals("HEAD"));
        System.out.println("includeAnswers: " + includeAnswers);
        System.out.println("isHead: " + isHead);
        System.out.println("Authorities: " + auth.getAuthorities());

        if (includeAnswers && !isHead) {
            throw new AccessDeniedException("Only admins or heads can export with answers.");
        }

        try {
            byte[] fileBytes;
            HttpHeaders headers = new HttpHeaders();
            String fileName = "quiz_" + quizId;

            if ("pdf".equalsIgnoreCase(format)) {
                fileBytes = quizExportService.exportQuizToPdf(quizId, includeAnswers);
                headers.setContentType(MediaType.APPLICATION_PDF);
                fileName += ".pdf";
            } else if ("word".equalsIgnoreCase(format)) {
                fileBytes = quizExportService.exportQuizToWord(quizId, includeAnswers);
                headers.setContentType(MediaType.APPLICATION_OCTET_STREAM); // Hoặc MediaType.APPLICATION_MSWORD nếu cần
                fileName += ".docx";
            } else {
                throw new IllegalArgumentException("Unsupported format. Use 'pdf' or 'word'.");
            }

            headers.setContentDispositionFormData("attachment", fileName);
            headers.setContentLength(fileBytes.length);

            return new ResponseEntity<>(fileBytes, headers, HttpStatus.OK);
        } catch (IOException e) {
            throw new RuntimeException("Failed to export quiz to " + format, e);
        }
    }
}