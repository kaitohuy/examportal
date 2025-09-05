package com.exam.examserver.dto.user;

public class UserWithRolesAndDeptDTO extends UserWithRolesDTO{

    private DepartmentDTO department;

    public DepartmentDTO getDepartment() {
        return department;
    }

    public void setDepartment(DepartmentDTO department) {
        this.department = department;
    }
}
