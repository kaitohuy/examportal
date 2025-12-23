// src/main/java/com/exam/examserver/dto/autogen/AutoGenRowDTO.java
package com.exam.examserver.dto.autogen;

import java.util.ArrayList;
import java.util.List;

public class AutoGenRowDTO {
    public String title;                    // "Câu 1"
    public List<AutoGenCellDTO> columns;    // size = variants
    public List<String> clos = new ArrayList<>();
}
