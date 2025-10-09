package com.exam.examserver.config;
import com.exam.examserver.controller.QuestionController;
import com.exam.examserver.service.impl.OpenAiSuggestService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // PHẢI public (và nên static). Có thể tách ra thành top-level public record/class nếu muốn.
    public static record ApiError(int status, String error, String message, String path) {}

    @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleNotFound(Exception ex, HttpServletRequest req) {
        return new ApiError(404, "Not Found", ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            org.springframework.web.bind.MethodArgumentNotValidException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleBadRequest(Exception ex, HttpServletRequest req) {
        return new ApiError(400, "Bad Request", ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ApiError handleTooLarge(Exception ex, HttpServletRequest req) {
        return new ApiError(413, "Payload Too Large", "File quá lớn", req.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError handleGeneric(Exception ex, HttpServletRequest req) {
        return new ApiError(500, "Internal Server Error", ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(OpenAiSuggestService.TooManyRequestsException.class)
    public ResponseEntity<Map<String, Object>> handle429(OpenAiSuggestService.TooManyRequestsException ex) {
        return ResponseEntity.status(429).body(Map.of(
                "status", 429,
                "error", "Too Many Requests",
                "message", "AI provider rate limit: please retry after ~" + ex.waitSeconds + "s",
                "path", "/questions/ai/suggest"
        ));
    }

    @ExceptionHandler(QuestionController.LocalRateLimitException.class)
    public ResponseEntity<Map<String,Object>> handleLocal429(QuestionController.LocalRateLimitException ex, HttpServletRequest req) {
        return ResponseEntity.status(429).body(Map.of(
                "status", 429,
                "error", "Too Many Requests",
                "message", ex.getMessage(),
                "path", req.getRequestURI()
        ));
    }
}
