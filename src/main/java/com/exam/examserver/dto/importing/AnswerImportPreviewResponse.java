// AnswerImportPreviewResponse.java
package com.exam.examserver.dto.importing;

import java.util.List;

public class AnswerImportPreviewResponse {
    public String sessionId;
    public int totalBlocks;
    public List<AnswerUpdatePreviewBlock> blocks;
}
