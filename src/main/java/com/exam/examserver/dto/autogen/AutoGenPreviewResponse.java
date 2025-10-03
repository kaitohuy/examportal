package com.exam.examserver.dto.autogen;

import java.math.BigDecimal;
import java.util.*;

public class AutoGenPreviewResponse {
    public int variants;
    public List<AutoGenRowDTO> rows;
    public List<List<Long>> paperQuestionIds;   // flatten mỗi đề -> list id (theo thứ tự row)
    public List<String> errors = new ArrayList<>();
    public BigDecimal[] paperTotals;            // tổng điểm mỗi đề
}

