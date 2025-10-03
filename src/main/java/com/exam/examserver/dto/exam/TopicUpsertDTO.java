package com.exam.examserver.dto.exam;

public class TopicUpsertDTO {
    private String code;
    private String name;
    private Integer orderIndex;
    private Long parentTopicId; // optional

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(Integer orderIndex) {
        this.orderIndex = orderIndex;
    }

    public Long getParentTopicId() {
        return parentTopicId;
    }

    public void setParentTopicId(Long parentTopicId) {
        this.parentTopicId = parentTopicId;
    }
}
