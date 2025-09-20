package com.exam.examserver.model.exam;

import com.exam.examserver.enums.Difficulty;
import com.exam.examserver.enums.QuestionLabel;
import com.exam.examserver.enums.QuestionType;
import com.exam.examserver.model.user.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "question")
public class Question {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;   // enum A–E

    @Column
    private int chapter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionType questionType; // enum MULTIPLE_CHOICE, ESSAY

    @Column(length = 10000, nullable = false)
    private String content;

    // Các trường cho câu trắc nghiệm
    @Column(nullable = true)
    private String optionA;
    @Column(nullable = true)
    private String optionB;
    @Column(nullable = true)
    private String optionC;
    @Column(nullable = true)
    private String optionD;
    @Column(nullable = true)
    private String answer; // A, B, C, D cho trắc nghiệm

    // Các trường cho câu tự luận
    @Column(length = 10000, nullable = true)
    private String answerText; // Đáp án dạng văn bản cho tự luận

    // Các trường cho câu hỏi có hình ảnh
    @Column(nullable = true)
    private String imageUrl; // URL hoặc path tới hình ảnh

    // Question.java  (bổ sung các field/quan hệ dưới)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Question parent;                 // null nếu là bản gốc

    @Column(name = "clone_index")
    private Integer cloneIndex;              // 1,2,3,... (chỉ dùng cho clone)

    // ĐỂ LAZY + batch để giảm N+1 khi cần load nhiều câu
    @ElementCollection(targetClass = QuestionLabel.class, fetch = FetchType.LAZY)
    @CollectionTable(name = "question_labels", joinColumns = @JoinColumn(name = "question_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "label", nullable = false)
    @BatchSize(size = 64) // Hibernate hint
    private Set<QuestionLabel> labels = new HashSet<>();

    // Với ảnh cũng nên batch để giảm N+1 khi mapper chạm images
    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orderIndex ASC")
    @BatchSize(size = 64)
    private List<QuestionImage> images = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "question", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private Set<QuizQuestion> quizQuestions = new HashSet<>();

    @JsonIgnore
    @OneToMany(mappedBy = "parent", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<Question> clones = new ArrayList<>();

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Subject getSubject() {
        return subject;
    }

    public void setSubject(Subject subject) {
        this.subject = subject;
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public Set<QuizQuestion> getQuizQuestions() {
        return quizQuestions;
    }

    public void setQuizQuestions(Set<QuizQuestion> quizQuestions) {
        this.quizQuestions = quizQuestions;
    }

    public List<QuestionImage> getImages() {
        return images;
    }

    public void setImages(List<QuestionImage> images) {
        this.images = images;
    }

    public Set<QuestionLabel> getLabels() {
        return labels;
    }

    public void setLabels(Set<QuestionLabel> labels) {
        this.labels = labels;
    }

    public Question getParent() {
        return parent;
    }

    public void setParent(Question parent) {
        this.parent = parent;
    }

    public Integer getCloneIndex() {
        return cloneIndex;
    }

    public void setCloneIndex(Integer cloneIndex) {
        this.cloneIndex = cloneIndex;
    }
}
