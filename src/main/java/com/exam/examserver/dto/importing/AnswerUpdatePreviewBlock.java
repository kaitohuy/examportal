// AnswerUpdatePreviewBlock.java
package com.exam.examserver.dto.importing;

import com.exam.examserver.enums.QuestionType;

import java.util.*;

public class AnswerUpdatePreviewBlock {

    // Thứ tự block trong file (#1, #2...)
    public int index;

    // Raw text toàn block (để debug nếu cần)
    public String raw;

    // Mã loại câu hỏi trích từ header: "1.1", "2.2", "2.1.3"...
    public String typeCode;

    // Prefix đoán theo labels: "NH" hoặc "OT"
    public String prefix;

    // ====== NEW: baseCode + include ======
    // Mã gốc dạng OT2.2 / NH3.1.4 (prefix + typeCode)
    public String baseCode;

    // FE tick chọn block này để commit hay không
    public boolean include = true;

    // Đoán kiểu câu hỏi
    public QuestionType questionType;

    // Nếu là MCQ: đáp án (A, AB, ...)
    public String mcAnswer;

    // Nếu là tự luận nhiều ý: a -> ..., b -> ...
    public Map<String, String> essayAnswers = new LinkedHashMap<>();

    // Mapping sang DB: "a" -> questionId, hoặc "" cho câu đơn
    public Map<String, Long> targetQuestionIds = new LinkedHashMap<>();

    // Đáp án hiện tại trong DB
    public Map<String, String> currentAnswers = new LinkedHashMap<>();

    // Đáp án mới sẽ cập nhật (đã normalize)
    public Map<String, String> newAnswers = new LinkedHashMap<>();

    // ít nhất 1 câu map được thì valid = true
    public boolean valid;

    public java.util.List<String> warnings = new ArrayList<>();
}
