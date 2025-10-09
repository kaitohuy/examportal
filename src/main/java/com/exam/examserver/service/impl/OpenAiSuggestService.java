package com.exam.examserver.service.impl;

import com.exam.examserver.dto.ai.AiSuggestRequest;
import com.exam.examserver.dto.ai.AiSuggestResponse;
import com.exam.examserver.enums.QuestionType;
import com.exam.examserver.service.AiSuggestService;
import com.exam.examserver.util.TextSim;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

/**
 * Gọi OpenAI /chat/completions để gợi ý đáp án.
 */
@Service
public class OpenAiSuggestService implements AiSuggestService {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ai.openai.apiBase}")
    private String apiBase;

    @Value("${ai.openai.apiKey:}")
    private String apiKey;

    @Value("${ai.openai.model:gpt-4o-mini}")
    private String model;

    @Value("${ai.openai.timeoutMs:15000}")
    private int timeoutMs;

    @Value("${ai.cacheTtlSeconds:86400}")
    private long cacheTtlSeconds;

    // cache cực gọn: key -> (value, expireAt)
    private static final class CacheEntry {
        AiSuggestResponse value;
        long expireAt;
    }
    private final LinkedHashMap<String, CacheEntry> cache = new LinkedHashMap<String, CacheEntry>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            return this.size() > 2000; // simple bound
        }
    };

    public OpenAiSuggestService(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public AiSuggestResponse suggest(AiSuggestRequest req) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("AI: OPENAI_API_KEY is not configured");
        }

        String cacheKey = hashRequest(req);
        AiSuggestResponse cached = getFromCache(cacheKey);
        if (cached != null) return cached;

        List<Map<String, String>> messages = buildMessages(req);

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("temperature", 0.1);
        // Ép OpenAI trả JSON
        payload.put("response_format", Map.of("type", "json_object"));
        payload.put("messages", messages);

        WebClient client = webClientBuilder
                .baseUrl(apiBase) // https://openrouter.ai/api
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader("HTTP-Referer", "https://examportal.local")
                .defaultHeader("X-Title", "ExamPortal")
                .build();


        Map<?, ?> result = client.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .onStatus(
                        status -> status.value() == 429,
                        resp -> {
                            String ra = resp.headers().asHttpHeaders().getFirst("Retry-After");
                            long waitSec = parseRetryAfter(ra);
                            return resp.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(body -> Mono.error(new TooManyRequestsException(
                                            "OpenAI 429" + (body.isEmpty() ? "" : (": " + body)), waitSec)));
                        }
                )
                .onStatus(
                        status -> status.is5xxServerError(),
                        resp -> resp.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new RuntimeException("OpenAI 5xx: " + resp.statusCode() + (body.isEmpty()? "" : (" " + body)))))
                )
                .bodyToMono(Map.class)
                .retryWhen(
                        Retry.backoff(3, Duration.ofMillis(600))
                                .maxBackoff(Duration.ofSeconds(4))
                                .jitter(0.2)
                                .filter(ex -> ex instanceof TooManyRequestsException || (ex.getMessage() != null && ex.getMessage().contains("5xx")))
                                .onRetryExhaustedThrow((spec, signal) -> signal.failure())
                )
                .timeout(Duration.ofMillis(timeoutMs))
                .block();

        String content = extractMessageContent(result);
        AiSuggestResponse parsed = parseToResponse(content);
        if (parsed == null) {
            parsed = new AiSuggestResponse();
            parsed.setReasoning("No structured JSON parsed from AI. Please review manually.");
            parsed.setConfidence(0.3);
        }
        parsed.setModel(model);
        parsed.setCacheKey(cacheKey);

        putToCache(cacheKey, parsed);
        return parsed;
    }

    // ===== helpers =====

    private List<Map<String, String>> buildMessages(AiSuggestRequest req) {
        String systemPrompt =
                "You are an assistant for teachers creating exam questions.\n" +
                        "Output STRICTLY a JSON object ONLY (no extra prose, no markdown fences).\n" +
                        "\n" +
                        "Language policy:\n" +
                        "- Respond in the SAME LANGUAGE as the user's question content. If the content is Vietnamese, reply in Vietnamese; if English, reply in English.\n" +
                        "\n" +
                        "Math formatting policy (MANDATORY):\n" +
                        "- All mathematical expressions must be written in LaTeX.\n" +
                        "- Use \\( ... \\) for inline math and \\[ ... \\] for display equations.\n" +
                        "- Do NOT use $...$ or $$...$$. Do NOT use markdown code blocks.\n" +
                        "\n" +
                        "JSON schema:\n" +
                        "- For MULTIPLE_CHOICE: {\"answer\":\"A|B|C|D\",\"optionScores\":{\"A\":number,\"B\":number,\"C\":number,\"D\":number},\"reasoning\":\"string\",\"confidence\":0.0-1.0}\n" +
                        "- For ESSAY: {\"answerText\":\"string\",\"reasoning\":\"string\",\"confidence\":0.0-1.0}\n" +
                        "\n" +
                        "Guidance:\n" +
                        "- Be concise but correct. If information is insufficient, make a best-effort assumption and state it briefly in reasoning.\n" +
                        "- Prefer step-by-step reasoning in the 'reasoning' field when appropriate (e.g., proofs, branch-and-bound steps, combinatorics), with LaTeX for all formulas.\n";

        StringBuilder userPrompt = new StringBuilder();
        if (req.getQuestionType() == com.exam.examserver.enums.QuestionType.MULTIPLE_CHOICE) {
            // Đóng gói MCQ để giảm nhiễu format
            String packed = com.exam.examserver.util.TextSim.packMultipleChoice(
                    req.getContent(), req.getOptionA(), req.getOptionB(), req.getOptionC(), req.getOptionD()
            );
            userPrompt.append("Type: MULTIPLE_CHOICE\n");
            userPrompt.append("Difficulty: ").append(String.valueOf(req.getDifficulty())).append("\n");
            userPrompt.append("Chapter: ").append(String.valueOf(req.getChapter())).append("\n");
            userPrompt.append("Question + Options (packed):\n").append(packed);
        } else {
            userPrompt.append("Type: ESSAY\n");
            userPrompt.append("Difficulty: ").append(String.valueOf(req.getDifficulty())).append("\n");
            userPrompt.append("Chapter: ").append(String.valueOf(req.getChapter())).append("\n");
            userPrompt.append("Prompt:\n").append(req.getContent());
        }

        List<Map<String, String>> msgs = new ArrayList<>();
        msgs.add(Map.of("role", "system", "content", systemPrompt));
        msgs.add(Map.of("role", "user", "content", userPrompt.toString()));
        return msgs;
    }

    private String extractMessageContent(Map<?, ?> result) {
        try {
            List<?> choices = (List<?>) result.get("choices");
            if (choices == null || choices.isEmpty()) return null;
            Map<?, ?> choice0 = (Map<?, ?>) choices.get(0);
            Map<?, ?> message = (Map<?, ?>) choice0.get("message");
            return (String) message.get("content");
        } catch (Exception e) {
            return null;
        }
    }

    private AiSuggestResponse parseToResponse(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            JsonNode node = objectMapper.readTree(json);
            AiSuggestResponse r = new AiSuggestResponse();
            if (node.has("answer")) r.setAnswer(s(node.get("answer")));
            if (node.has("answerText")) r.setAnswerText(s(node.get("answerText")));
            if (node.has("reasoning")) r.setReasoning(s(node.get("reasoning")));
            if (node.has("confidence")) r.setConfidence(node.get("confidence").asDouble(0.5));

            if (node.has("optionScores") && node.get("optionScores").isObject()) {
                Map<String, Double> scores = new LinkedHashMap<>();
                Iterator<String> it = node.get("optionScores").fieldNames();
                while (it.hasNext()) {
                    String k = it.next();
                    scores.put(k, node.get("optionScores").get(k).asDouble(0));
                }
                r.setOptionScores(scores);
            }
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    private String s(JsonNode n) { return (n == null || n.isNull()) ? null : n.asText(); }

    private String hashRequest(AiSuggestRequest r) {
        try {
            String packed;
            if (r.getQuestionType() == QuestionType.MULTIPLE_CHOICE) {
                String mcq = TextSim.packMultipleChoice(
                        r.getContent(), r.getOptionA(), r.getOptionB(), r.getOptionC(), r.getOptionD()
                );
                packed = "MCQ|" + String.valueOf(r.getDifficulty()) + "|" + r.getChapter() + "|" + mcq;
            } else {
                packed = "ESSAY|" + String.valueOf(r.getDifficulty()) + "|" + r.getChapter() + "|" + r.getContent();
            }
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] b = md.digest(packed.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    private synchronized AiSuggestResponse getFromCache(String key) {
        long now = System.currentTimeMillis();
        CacheEntry ce = cache.get(key);
        if (ce != null && ce.expireAt > now) {
            return ce.value;
        }
        return null;
    }

    private synchronized void putToCache(String key, AiSuggestResponse value) {
        CacheEntry ce = new CacheEntry();
        ce.value = value;
        ce.expireAt = System.currentTimeMillis() + cacheTtlSeconds * 1000;
        cache.put(key, ce);
    }

    public static class TooManyRequestsException extends RuntimeException {
        public final long waitSeconds;
        TooManyRequestsException(String msg, long waitSeconds) { super(msg); this.waitSeconds = waitSeconds; }
    }

    public static long parseRetryAfter(String retryAfterHeader) {
        try {
            return Math.max(1L, Long.parseLong(retryAfterHeader.trim()));
        } catch (Exception e) {
            return 1L;
        }
    }

    public static class LocalRateLimitException extends RuntimeException {
        public LocalRateLimitException(String msg) { super(msg); }
    }

// trong guardRate(...)

}
