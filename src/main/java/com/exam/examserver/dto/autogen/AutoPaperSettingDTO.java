// src/main/java/com/exam/examserver/dto/autogen/AutoPaperSettingDTO.java
package com.exam.examserver.dto.autogen;

import com.exam.examserver.enums.QuestionLabel;
import java.util.List;
import java.util.Set;

public class AutoPaperSettingDTO {
    public Long id;
    public Long subjectId;
    public String name;
    public int variants;                 // NEW
    public Set<QuestionLabel> labelScope;
    public boolean noRepeatWithin;
    public boolean noRepeatAcross;
    public Integer notUsedYears;
    public List<AutoGenStepDTO> steps;
}
