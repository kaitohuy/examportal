package com.exam.examserver.dto.autogen;

import java.util.List;

public class AutoGenStepDTO {
    public enum Grouping { SINGLE, BUNDLE } // SINGLE -> 1 selector; BUNDLE -> >=2 selectors (câu ghép)
    public String title;                    // "Câu 1", "Câu 2", ...
    public Grouping grouping;
    public List<AutoGenSelectorDTO> selectors;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Grouping getGrouping() {
        return grouping;
    }

    public void setGrouping(Grouping grouping) {
        this.grouping = grouping;
    }

    public List<AutoGenSelectorDTO> getSelectors() {
        return selectors;
    }

    public void setSelectors(List<AutoGenSelectorDTO> selectors) {
        this.selectors = selectors;
    }
}
