package com.exam.examserver.dto.exam;

import java.util.List;

public class BulkSelectionRequest {
    public enum Mode { IDS, FILTER }
    private Mode mode;

    // mode=IDS
    private List<Long> ids;

    // mode=FILTER
    private QuestionFilter filter;
    private List<Long> excludeIds;

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }

    public List<Long> getIds() { return ids; }
    public void setIds(List<Long> ids) { this.ids = ids; }

    public QuestionFilter getFilter() { return filter; }
    public void setFilter(QuestionFilter filter) { this.filter = filter; }

    public List<Long> getExcludeIds() { return excludeIds; }
    public void setExcludeIds(List<Long> excludeIds) { this.excludeIds = excludeIds; }
}
