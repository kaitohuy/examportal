package com.exam.examserver.dto.importing;

import com.exam.examserver.enums.Difficulty;
import com.exam.examserver.enums.QuestionLabel;
import com.exam.examserver.enums.QuestionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CommitBlock {
    public int index;                     // block index (1-based)
    public boolean include = true;        // có import không
    public QuestionType questionType;
    public Difficulty difficulty = Difficulty.C;
    public int chapter = 0;

    public String content;
    public String optionA, optionB, optionC, optionD;
    public String answer;
    public String answerText;
    public Set<QuestionLabel> labels;
    public List<Integer> imageIndexes = new ArrayList<>(); // ảnh được chọn để import
}
