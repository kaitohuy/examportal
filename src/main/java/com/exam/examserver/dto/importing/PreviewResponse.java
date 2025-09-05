package com.exam.examserver.dto.importing;

import java.util.ArrayList;
import java.util.List;

public class PreviewResponse {
    public String sessionId;
    public int totalBlocks;
    public List<PreviewBlock> blocks = new ArrayList<>();
}
