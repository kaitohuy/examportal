package com.exam.examserver.dto.importing;

import java.util.List;

public class ImportResult {
    private int totalBlocks;
    private int successCount;
    private List<String> errors;  // mô tả block nào lỗi, vì sao
    // + getters/setters, constructor


    public ImportResult() {
    }

    public ImportResult(int totalBlocks, int successCount, List<String> errors) {
        this.totalBlocks = totalBlocks;
        this.successCount = successCount;
        this.errors = errors;
    }

    public int getTotalBlocks() {
        return totalBlocks;
    }

    public void setTotalBlocks(int totalBlocks) {
        this.totalBlocks = totalBlocks;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }
}

