package com.exam.examserver.dto.autogen;

import java.util.List;

public class AutoGenRequest {
    public int variants = 1;                 // số đề
    public boolean noRepeatWithinPaper = true;
    public boolean noRepeatAcrossPapers = true;
    public List<AutoGenStepDTO> steps;       // thứ tự chính là cấu trúc đề
}

