package com.exam.examserver.dto.autogen;

import java.math.BigDecimal;
import java.util.List;

public class AutoGenCellDTO {
    public List<Long> questionIds;          // ô có thể là 1 id (SINGLE) hoặc nhiều id (BUNDLE)
    public BigDecimal totalPoints;          // tổng điểm của ô (= sum meta.points)
}

