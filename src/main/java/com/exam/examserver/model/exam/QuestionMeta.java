// src/main/java/com/exam/examserver/model/exam/QuestionMeta.java
package com.exam.examserver.model.exam;

import com.exam.examserver.enums.CognitiveLevel;
import com.exam.examserver.enums.ItemNature;
import com.exam.examserver.enums.RecordStatus;
import com.exam.examserver.enums.UnitKind;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "question_meta")
public class QuestionMeta {

    @Id
    @Column(name = "question_id")
    private Long questionId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId // <-- CHỐT: shared PK lấy từ Question
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_kind", nullable = false, length = 32)
    private UnitKind unitKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "cognitive_level", length = 32)
    private CognitiveLevel cognitiveLevel;

    @Column(name = "type_code", length = 64)
    private String typeCode;

    @Column(name = "points", precision = 10, scale = 2, nullable = false)
    private BigDecimal points;

    @Column(name = "chapter")
    private Integer chapter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")
    private Topic topic;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "usage_count")
    private Long usageCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RecordStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_nature", length = 20)
    private ItemNature itemNature;

    @Column(name = "problem_type")
    private String problemType;

    @PrePersist
    public void prePersist() {
        if (usageCount == null) usageCount = 0L;
        if (points == null) points = new BigDecimal("1.00");
        if (unitKind == null) unitKind = UnitKind.FULL_QUESTION;
        if (status == null) status = RecordStatus.DRAFT;
    }

    public String getProblemType() {
        return problemType;
    }

    public void setProblemType(String problemType) {
        this.problemType = problemType;
    }

    public ItemNature getItemNature() {
        return itemNature;
    }

    public void setItemNature(ItemNature itemNature) {
        this.itemNature = itemNature;
    }

    // getters/setters
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; } // có cũng được, nhưng không cần dùng
    public Question getQuestion() { return question; }
    public void setQuestion(Question question) { this.question = question; }
    public UnitKind getUnitKind() { return unitKind; }
    public void setUnitKind(UnitKind unitKind) { this.unitKind = unitKind; }
    public CognitiveLevel getCognitiveLevel() { return cognitiveLevel; }
    public void setCognitiveLevel(CognitiveLevel cognitiveLevel) { this.cognitiveLevel = cognitiveLevel; }
    public String getTypeCode() { return typeCode; }
    public void setTypeCode(String typeCode) { this.typeCode = typeCode; }
    public BigDecimal getPoints() { return points; }
    public void setPoints(BigDecimal points) { this.points = points; }
    public Integer getChapter() { return chapter; }
    public void setChapter(Integer chapter) { this.chapter = chapter; }
    public Topic getTopic() { return topic; }
    public void setTopic(Topic topic) { this.topic = topic; }
    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public Long getUsageCount() { return usageCount; }
    public void setUsageCount(Long usageCount) { this.usageCount = usageCount; }
    public RecordStatus getStatus() { return status; }
    public void setStatus(RecordStatus status) { this.status = status; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QuestionMeta that)) return false;
        return Objects.equals(questionId, that.questionId);
    }
    @Override
    public int hashCode() { return Objects.hashCode(questionId); }
}
