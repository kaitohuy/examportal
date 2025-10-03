package com.exam.examserver.model.exam;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;

@Entity
@Table(name = "bundle_item",
        uniqueConstraints = @UniqueConstraint(name="uq_bitem_bundle_order",
                columnNames={"bundle_id","order_index"}))
@BatchSize(size = 128)
public class BundleItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(name = "points_override", precision = 5, scale = 2)
    private BigDecimal pointsOverride;

    @Column(length = 255)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bundle_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private QuestionBundle bundle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Question question;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public QuestionBundle getBundle() {
        return bundle;
    }

    public void setBundle(QuestionBundle bundle) {
        this.bundle = bundle;
    }

    public Integer getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(Integer orderIndex) {
        this.orderIndex = orderIndex;
    }

    public Question getQuestion() {
        return question;
    }

    public void setQuestion(Question question) {
        this.question = question;
    }

    public BigDecimal getPointsOverride() {
        return pointsOverride;
    }

    public void setPointsOverride(BigDecimal pointsOverride) {
        this.pointsOverride = pointsOverride;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

}
