package com.exam.examserver.model.exam;

import com.exam.examserver.enums.RecordStatus;
import com.exam.examserver.model.user.User;
import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "question_bundle")
@BatchSize(size = 64)
public class QuestionBundle {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) // bundle theo từng môn
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    private String title;                // "Question 1" (optional)
    @Column(columnDefinition = "text")
    private String instructions;         // mô tả chung

    @Column(name = "total_points", precision = 5, scale = 2)
    private BigDecimal totalPoints;      // null => tính từ items

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordStatus status = RecordStatus.DRAFT;

    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;              // nếu có bảng user

    @OneToMany(mappedBy = "bundle",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("orderIndex ASC")
    @BatchSize(size = 128)
    private List<BundleItem> items = new ArrayList<>();

    // helpers
    public void addItem(BundleItem item) {
        item.setBundle(this);
        this.items.add(item);
    }
    public void removeItem(BundleItem item) {
        this.items.remove(item);
        item.setBundle(null);
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public BigDecimal getTotalPoints() {
        return totalPoints;
    }

    public void setTotalPoints(BigDecimal totalPoints) {
        this.totalPoints = totalPoints;
    }

    public RecordStatus getStatus() {
        return status;
    }

    public void setStatus(RecordStatus status) {
        this.status = status;
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

    public List<BundleItem> getItems() {
        return items;
    }

    public void setItems(List<BundleItem> items) {
        this.items = items;
    }
}
