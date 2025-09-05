package com.exam.examserver.service;

import com.exam.examserver.dto.user.DepartmentDTO;
import com.exam.examserver.model.user.Department;

import java.util.List;

public interface DepartmentService {

    DepartmentDTO createDepartment(DepartmentDTO departmentDTO);

    Department updateDepartment(Long id, Department department);

    void deleteDepartment(Long id);

    Department getDepartmentById(Long id);

    List<Department> getAllDepartments();

    List<Department> getDepartmentsByHeadUserId(Long headUserId);
}


