package com.exam.examserver.dto.user;

public class DepartmentDTO {
    private Long id;
    private String name;
    private String description;
    private UserBasicDTO headUser;

    public DepartmentDTO(Long id, String name, String description, UserBasicDTO headUser) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.headUser = headUser;
    }

    // Getters và setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UserBasicDTO getHeadUser() {
        return headUser;
    }

    public void setHeadUser(UserBasicDTO headUser) {
        this.headUser = headUser;
    }
}

