package com.exam.examserver.dto.importing;

import java.util.ArrayList;
import java.util.List;

public class AnswerImportResult {
    public int totalBlocks;      // số block được tick
    public int totalQuestions;   // số câu hỏi thực sự update thành công
    public int notFound;         // số mã không tìm thấy trong DB
    public List<String> errors = new ArrayList<>();
}