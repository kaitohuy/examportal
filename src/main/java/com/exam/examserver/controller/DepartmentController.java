package com.exam.examserver.controller;

import com.exam.examserver.dto.user.DepartmentDTO;
import com.exam.examserver.dto.user.DepartmentStatsDTO;
import com.exam.examserver.enums.RoleType;
import com.exam.examserver.mapper.DepartmentMapper;
import com.exam.examserver.mapper.UserMapper;
import com.exam.examserver.model.user.Department;
import com.exam.examserver.repo.SubjectRepository;
import com.exam.examserver.repo.UserRepository;
import com.exam.examserver.service.DepartmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/department")
@CrossOrigin("*")
public class DepartmentController {

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DepartmentMapper departmentMapper;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private UserMapper userMapper;

    @PostMapping("/")
    public ResponseEntity<DepartmentDTO> createDepartment(@RequestBody DepartmentDTO dto) {
        DepartmentDTO result = departmentService.createDepartment(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Department> updateDepartment(
            @PathVariable Long id,
            @RequestBody Department department) {
        Department updated = departmentService.updateDepartment(id, department);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/")
    public ResponseEntity<List<DepartmentDTO>> getAllDepartments() {
        List<Department> departments = departmentService.getAllDepartments();
        return ResponseEntity.ok(departmentMapper.toDto(departments));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DepartmentDTO> getDepartmentById(@PathVariable Long id) {
        Department department = departmentService.getDepartmentById(id);
        return ResponseEntity.ok(departmentMapper.toDto(department));
    }

    @GetMapping("/head/{headUserId}")
    public ResponseEntity<List<DepartmentDTO>> getByHeadUser(@PathVariable Long headUserId) {
        List<Department> depts = departmentService.getDepartmentsByHeadUserId(headUserId);
        return ResponseEntity.ok(departmentMapper.toDto(depts)); // <-- TRẢ DTO
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDepartment(@PathVariable Long id) {
        departmentService.deleteDepartment(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/stats")
    public DepartmentStatsDTO stats(@PathVariable Long id) {
        DepartmentStatsDTO dto = new DepartmentStatsDTO();
        dto.setSubjectCount(subjectRepository.countByDepartmentId(id));
        dto.setTeacherCount(userRepository.countByRoleAndDepartment(RoleType.TEACHER, id));
        dto.setUnassignedSubjectCount(subjectRepository.countUnassignedByDepartmentId(id)); // optional
        return dto;
    }
}

