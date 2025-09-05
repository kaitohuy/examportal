package com.exam.examserver.dto.exam;

public class SubjectDTO {
    private Long id;
    private String name;
    private String code;
    private Long departmentId;

    public SubjectDTO() {} // no-args

    public SubjectDTO(Long id, String name, String code, Long departmentId) {
        this.id = id;
        this.name = name;
        this.code = code;
        this.departmentId = departmentId;
    }
    //getter/setter

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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }
}