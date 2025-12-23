package com.exam.examserver.dto.importing;

import com.exam.examserver.enums.Difficulty;
import com.exam.examserver.enums.QuestionLabel;
import com.exam.examserver.enums.QuestionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PreviewBlock {
    public int index;                     // 1-based
    public QuestionType questionType;     // MC/ESSAY suy ra từ parser
    public Difficulty difficulty = Difficulty.C;
    public int chapter = 0;
    public String raw;                    // block thô (tuỳ bạn có muốn trả hay không)
    public String content;                // stem/content
    public String optionA, optionB, optionC, optionD;
    public String answer;                 // MC
    public String answerText;
    public Set<QuestionLabel> labels;
    public List<Integer> imageIndexes = new ArrayList<>();
    public List<String> warnings = new ArrayList<>();
    public Double duplicateScore;
    public List<Long> duplicateOfIds;

    public String headerNo;        // "1.1" / "2.1.1" ...
    public String previewPrefix;   // "OT"/"TC"
    public String previewCode;     // nếu câu đơn
    public List<String> previewSubCodes; // nếu tách a)/b)/c)

    public Double duplicateBundleScore;   // điểm best với bundle
    public List<Long> duplicateBundleIds; // list ứng viên bundle

    // ===== NEW: clone info =====
    public String declaredCode;                // raw chuỗi trong (Mã: ...)
    public String cloneBaseCode;               // ví dụ "OT2.1.1"
    public Integer cloneDesiredIndex;          // nếu user ghi C3. ... thì = 3, ngược lại null
    public Integer cloneNextIndex;             // dự kiến index sẽ dùng (đã tính toán)
    public String clonePreviewCode;

    public String problemType;
}