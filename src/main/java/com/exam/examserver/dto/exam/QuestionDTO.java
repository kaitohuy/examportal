package com.exam.examserver.dto.exam;

import com.exam.examserver.dto.user.UserBasicDTO;
import com.exam.examserver.enums.Difficulty;
import com.exam.examserver.enums.QuestionLabel;
import com.exam.examserver.enums.QuestionType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

// Dùng để hiển thị câu hỏi
public class QuestionDTO {
    private Long id;
    private QuestionType questionType; // Thêm questionType
    private String content;
    private Difficulty difficulty;
    private int chapter;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    private String answer; // Cho trắc nghiệm
    private String answerText; // Cho tự luận
    private String imageUrl; // Cho câu hỏi hình ảnh
    private LocalDateTime createdAt;
    private UserBasicDTO createdBy;

    private Set<QuestionLabel> labels;
    private List<QuestionImageDTO> images;

    private Long parentId;
    private Integer cloneIndex;

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public int getChapter() {
        return chapter;
    }

    public void setChapter(int chapter) {
        this.chapter = chapter;
    }

    public String getOptionB() {
        return optionB;
    }

    public void setOptionB(String optionB) {
        this.optionB = optionB;
    }

    public String getOptionA() {
        return optionA;
    }

    public void setOptionA(String optionA) {
        this.optionA = optionA;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public UserBasicDTO getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UserBasicDTO createdBy) {
        this.createdBy = createdBy;
    }

    public QuestionType getQuestionType() {
        return questionType;
    }

    public void setQuestionType(QuestionType questionType) {
        this.questionType = questionType;
    }

    public String getAnswerText() {
        return answerText;
    }

    public void setAnswerText(String answerText) {
        this.answerText = answerText;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public List<QuestionImageDTO> getImages() { return images; }
    public void setImages(List<QuestionImageDTO> images) { this.images = images; }

    public Set<QuestionLabel> getLabels() {
        return labels;
    }

    public void setLabels(Set<QuestionLabel> labels) {
        this.labels = labels;
    }

    public Integer getCloneIndex() {
        return cloneIndex;
    }

    public void setCloneIndex(Integer cloneIndex) {
        this.cloneIndex = cloneIndex;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }
}
