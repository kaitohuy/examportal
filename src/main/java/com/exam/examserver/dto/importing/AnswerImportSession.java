package com.exam.examserver.dto.importing;

import java.util.List;

//public class AnswerImportSession {
//    public String id;
//    public List<AnswerUpdatePreviewBlock> blocks;
//
//    public AnswerImportSession(String id, List<AnswerUpdatePreviewBlock> blocks) {
//        this.id = id;
//        this.blocks = blocks;
//    }
//}

public record AnswerImportSession(
        String id,
        List<AnswerUpdatePreviewBlock> blocks
) {}
