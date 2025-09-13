package com.exam.examserver.dto.exam;

public class SubjectDTO {
    private Long id;
    private String name;
    private String code;
    private Long departmentId;
    private String departmentName;

    public SubjectDTO() {} // no-args

    public SubjectDTO(Long id, String name, String code, Long departmentId, String departmentName) {
        this.id = id;
        this.name = name;
        this.code = code;
        this.departmentId = departmentId;
        this.departmentName = departmentName;
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

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }
}