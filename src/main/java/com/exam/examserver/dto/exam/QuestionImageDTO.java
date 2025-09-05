package com.exam.examserver.dto.exam;

public class QuestionImageDTO {
    private Long id;
    private String url;
    private Integer orderIndex;
    private String caption;

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public Integer getOrderIndex() { return orderIndex; }
    public void setOrderIndex(Integer orderIndex) { this.orderIndex = orderIndex; }
    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }
}
