package com.exam.examserver.model.exam;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "question_image",
        uniqueConstraints = @UniqueConstraint(columnNames = {"question_id", "order_index"}))
public class QuestionImage {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "url", nullable = false, length = 1000)
    private String url;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "caption")
    private String caption;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public QuestionImage() {
    }

    public QuestionImage(Question question, String url, int orderIndex, String caption, LocalDateTime createdAt) {
        this.question = question;
        this.url = url;
        this.orderIndex = orderIndex;
        this.caption = caption;
        this.createdAt = createdAt;
    }

    // getters/setters
    public Long getId() { return id; }
    public Question getQuestion() { return question; }
    public void setQuestion(Question question) { this.question = question; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public int getOrderIndex() { return orderIndex; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }
    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
