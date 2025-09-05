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
    public String answerText;             // Essay
    public Set<QuestionLabel> labels;
    public List<Integer> imageIndexes = new ArrayList<>(); // tham chiếu image list trong session
    public List<String> warnings = new ArrayList<>();
}