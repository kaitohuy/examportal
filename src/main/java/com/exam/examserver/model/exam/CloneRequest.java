package com.exam.examserver.model.exam;

import com.exam.examserver.enums.Difficulty;
import com.exam.examserver.enums.QuestionLabel;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CloneRequest {

    private int count = 1;
    private Set<QuestionLabel> labels;
    private Difficulty difficulty;
    private Integer chapter;
    private Boolean copyImages = Boolean.TRUE;

    public CloneRequest() {}

    public int getCount() {
        return count <= 0 ? 1 : count;
    }
    public void setCount(int count) {
        this.count = count;
    }

    public Set<QuestionLabel> getLabels() {
        return labels;
    }
    public void setLabels(Set<QuestionLabel> labels) {
        this.labels = labels;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }
    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public Integer getChapter() {
        return chapter;
    }
    public void setChapter(Integer chapter) {
        this.chapter = chapter;
    }

    public Boolean getCopyImages() {
        return copyImages != null ? copyImages : Boolean.TRUE;
    }
    public void setCopyImages(Boolean copyImages) {
        this.copyImages = copyImages;
    }
}
