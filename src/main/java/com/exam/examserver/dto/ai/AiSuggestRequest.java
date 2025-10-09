package com.exam.examserver.dto.ai;

import com.exam.examserver.enums.Difficulty;
import com.exam.examserver.enums.QuestionType;

/**
 * Dữ liệu gửi từ FE khi người dùng yêu cầu AI gợi ý đáp án.
 */
public class AiSuggestRequest {

    private QuestionType questionType;   // MULTIPLE_CHOICE hoặc ESSAY
    private String content;              // nội dung câu hỏi
    private Difficulty difficulty;       // A-E
    private Integer chapter;             // chương (nếu có)

    // Cho trắc nghiệm
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;

    // ===== Getter/Setter =====
    public QuestionType getQuestionType() {
        return questionType;
    }

    public void setQuestionType(QuestionType questionType) {
        this.questionType = questionType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public Integer getChapter() {
        return chapter;
    }

    public void setChapter(Integer chapter) {
        this.chapter = chapter;
    }

    public String getOptionA() {
        return optionA;
    }

    public void setOptionA(String optionA) {
        this.optionA = optionA;
    }

    public String getOptionB() {
        return optionB;
    }

    public void setOptionB(String optionB) {
        this.optionB = optionB;
    }

    public String getOptionC() {
        return optionC;
    }

    public void setOptionC(String optionC) {
        this.optionC = optionC;
    }

    public String getOptionD() {
        return optionD;
    }

    public void setOptionD(String optionD) {
        this.optionD = optionD;
    }

    @Override
    public String toString() {
        return "AiSuggestRequest{" +
                "questionType=" + questionType +
                ", content='" + content + '\'' +
                ", difficulty=" + difficulty +
                ", chapter=" + chapter +
                ", optionA='" + optionA + '\'' +
                ", optionB='" + optionB + '\'' +
                ", optionC='" + optionC + '\'' +
                ", optionD='" + optionD + '\'' +
                '}';
    }
}
