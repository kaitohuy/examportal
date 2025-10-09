package com.exam.examserver.dto.ai;

import java.util.Map;

/**
 * Kết quả AI trả về sau khi gợi ý đáp án.
 */
public class AiSuggestResponse {

    // Cho MULTIPLE_CHOICE
    private String answer;                      // "A" | "B" | "C" | "D"

    // Cho ESSAY
    private String answerText;                  // câu trả lời tự luận

    // Độ tin cậy 0.0 - 1.0
    private double confidence;

    // Giải thích ngắn gọn
    private String reasoning;

    // Xác suất từng lựa chọn (chỉ cho MULTIPLE_CHOICE)
    private Map<String, Double> optionScores;

    // Dùng để debug
    private String model;
    private String cacheKey;

    // ===== Getter/Setter =====
    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getAnswerText() {
        return answerText;
    }

    public void setAnswerText(String answerText) {
        this.answerText = answerText;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public Map<String, Double> getOptionScores() {
        return optionScores;
    }

    public void setOptionScores(Map<String, Double> optionScores) {
        this.optionScores = optionScores;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getCacheKey() {
        return cacheKey;
    }

    public void setCacheKey(String cacheKey) {
        this.cacheKey = cacheKey;
    }

    @Override
    public String toString() {
        return "AiSuggestResponse{" +
                "answer='" + answer + '\'' +
                ", answerText='" + answerText + '\'' +
                ", confidence=" + confidence +
                ", reasoning='" + reasoning + '\'' +
                ", optionScores=" + optionScores +
                ", model='" + model + '\'' +
                ", cacheKey='" + cacheKey + '\'' +
                '}';
    }
}
