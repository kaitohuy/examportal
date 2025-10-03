package com.exam.examserver.dto.exam;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.exam.examserver.enums.RecordStatus;

public class QuestionBundleDTO {
    private Long id;
    private Long subjectId;
    private String title;
    private String instructions;
    private BigDecimal totalPoints;     // null => FE/BE tự cộng từ items
    private RecordStatus status;
    private LocalDateTime createdAt;
    private Long createdById;           // flatten
    private List<BundleItemDTO> items;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
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

    public Long getCreatedById() {
        return createdById;
    }

    public void setCreatedById(Long createdById) {
        this.createdById = createdById;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<BundleItemDTO> getItems() {
        return items;
    }

    public void setItems(List<BundleItemDTO> items) {
        this.items = items;
    }
}
