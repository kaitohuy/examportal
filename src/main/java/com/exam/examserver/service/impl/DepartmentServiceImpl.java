package com.exam.examserver.service.impl;

import com.exam.examserver.dto.user.DepartmentDTO;
import com.exam.examserver.mapper.DepartmentMapper;
import com.exam.examserver.model.user.Department;
import com.exam.examserver.model.user.User;
import com.exam.examserver.repo.DepartmentRepository;
import com.exam.examserver.repo.UserRepository;
import com.exam.examserver.service.DepartmentService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DepartmentServiceImpl implements DepartmentService {

    @Autowired
    private DepartmentRepository departmentRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DepartmentMapper departmentMapper;

    @Override
    @Transactional
    public DepartmentDTO createDepartment(DepartmentDTO dto) {

        User headUser = userRepository
                .findById(dto.getHeadUser().getId())
                .orElseThrow(() -> new EntityNotFoundException("Head user not found"));

        Department entity = departmentMapper.toEntity(dto);
        entity.setHeadUser(headUser);
        Department saved = departmentRepository.save(entity);

        return departmentMapper.toDto(saved);
    }

    @Override
    public Department updateDepartment(Long id, Department updatedDepartment) {
        Department existing = departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Department not found"));

        existing.setName(updatedDepartment.getName());
        existing.setDescription(updatedDepartment.getDescription());
        existing.setHeadUser(updatedDepartment.getHeadUser());

        return departmentRepository.save(existing);
    }

    @Override
    public void deleteDepartment(Long id) {
        departmentRepository.deleteById(id);
    }

    @Override
    public Department getDepartmentById(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Department not found"));
    }

    @Override
    public List<Department> getAllDepartments() {
        return departmentRepository.findAll();
    }

    @Override
    public List<Department> getDepartmentsByHeadUserId(Long headUserId) {
        return departmentRepository.findByHeadUserId(headUserId);
    }
}

