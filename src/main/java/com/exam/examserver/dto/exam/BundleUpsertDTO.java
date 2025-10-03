package com.exam.examserver.dto.exam;

import java.math.BigDecimal;
import java.util.List;
import com.exam.examserver.enums.RecordStatus;

public class BundleUpsertDTO {
    private String title;
    private String instructions;
    private BigDecimal totalPoints;        // optional
    private RecordStatus status;           // DRAFT by default
    private List<BundleUpsertItemDTO> items;

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

    public RecordStatus getStatus() {
        return status;
    }

    public void setStatus(RecordStatus status) {
        this.status = status;
    }

    public BigDecimal getTotalPoints() {
        return totalPoints;
    }

    public void setTotalPoints(BigDecimal totalPoints) {
        this.totalPoints = totalPoints;
    }

    public List<BundleUpsertItemDTO> getItems() {
        return items;
    }

    public void setItems(List<BundleUpsertItemDTO> items) {
        this.items = items;
    }
}
