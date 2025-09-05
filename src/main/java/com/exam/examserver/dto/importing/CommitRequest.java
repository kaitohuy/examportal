package com.exam.examserver.dto.importing;

import java.util.ArrayList;
import java.util.List;

public class CommitRequest {
    public String sessionId;
    public List<CommitBlock> blocks = new ArrayList<>(); // các block đã chỉnh sửa
}
