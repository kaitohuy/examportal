package com.exam.examserver.controller;

import com.exam.examserver.dto.exam.SubjectDTO;
import com.exam.examserver.dto.exam.SubjectWithTeachersDTO;
import com.exam.examserver.dto.user.TeacherAssignmentDTO;
import com.exam.examserver.mapper.SubjectMapper;
import com.exam.examserver.mapper.SubjectWithTeachersMapper;
import com.exam.examserver.model.exam.Subject;
import com.exam.examserver.model.user.CustomUserDetails;
import com.exam.examserver.model.user.Department;
import com.exam.examserver.model.user.TeacherSubject;
import com.exam.examserver.repo.DepartmentRepository;
import com.exam.examserver.repo.SubjectRepository;
import com.exam.examserver.repo.TeacherSubjectRepository;
import com.exam.examserver.service.SubjectService;
import com.exam.examserver.service.TeacherSubjectService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/subject")
@CrossOrigin("*")
public class SubjectController {

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private SubjectService subjectService;

    @Autowired
    private SubjectMapper subjectMapper; // Cho CRUD cơ bản

    @Autowired
    private SubjectWithTeachersMapper subjectWithTeachersMapper; // Cho trường hợp có teachers

    @Autowired
    private TeacherSubjectService teacherSubjectService;

    @Autowired
    private TeacherSubjectRepository teacherSubjectRepo;

    // Sử dụng subjectMapper cho các endpoint cơ bản
    @GetMapping("/")
    public ResponseEntity<List<SubjectDTO>> getAllSubjects() {
        List<Subject> subjects = subjectService.getAllSubjects();
        return ResponseEntity.ok(subjectMapper.toDtoList(subjects));
    }

    // Sử dụng subjectWithTeachersMapper khi cần thông tin giáo viên
    @GetMapping("/{id}")
    public ResponseEntity<SubjectWithTeachersDTO> getSubjectById(@PathVariable Long id) {
        Subject subject = subjectService.getSubjectByIdWithTeachers(id);
        return ResponseEntity.ok(subjectWithTeachersMapper.toDto(subject));
    }

    // API lấy theo departmentId
    @GetMapping("/department/{departmentId}")
    public ResponseEntity<List<SubjectDTO>> getByDepartment(
            @PathVariable Long departmentId) {
        List<Subject> subjects = subjectService.getSubjectsByDepartmentId(departmentId);
        List<SubjectDTO> dtos = subjects.stream()
                .map(subjectMapper::toDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/")
    public ResponseEntity<SubjectDTO> createSubject(@RequestBody SubjectDTO subjectDTO) {
        if (subjectRepository.existsByCode(subjectDTO.getCode())) {
            throw new IllegalArgumentException("Subject with code '" + subjectDTO.getCode() + "' already exists.");
        }

        Department department = departmentRepository.findById(subjectDTO.getDepartmentId())
                .orElseThrow(() -> new EntityNotFoundException("Department not found"));

        Subject subject = subjectMapper.toEntity(subjectDTO);
        subject.setDepartment(department); // gán trước khi lưu

        Subject created = subjectService.createSubject(subject);
        return ResponseEntity.status(HttpStatus.CREATED).body(subjectMapper.toDto(created));
    }


    @PutMapping("/{id}")
    public ResponseEntity<SubjectDTO> updateSubject(
            @PathVariable Long id,
            @RequestBody SubjectDTO subjectDTO) {
        Subject updated = subjectService.updateSubject(id, subjectMapper.toEntity(subjectDTO));
        return ResponseEntity.ok(subjectMapper.toDto(updated));
    }

    // Xóa môn học
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSubject(@PathVariable Long id) {
        subjectService.deleteSubject(id);
        return ResponseEntity.noContent().build();
    }

    // PHÂN CÔNG GIẢNG DẠY - tích hợp vào Subject Controller
    @PostMapping("/{subjectId}/teachers")
    public ResponseEntity<Void> assignTeacherToSubject(
            @PathVariable Long subjectId,
            @RequestBody TeacherAssignmentDTO dto) {
        teacherSubjectService.assignTeacherToSubject(subjectId, dto.getTeacherId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{subjectId}/teachers/{teacherId}")
    public ResponseEntity<Void> removeTeacherFromSubject(
            @PathVariable Long subjectId,
            @PathVariable Long teacherId) {
        teacherSubjectService.removeTeacherFromSubject(subjectId, teacherId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/my")
    @PreAuthorize("hasAuthority('TEACHER')") // tuỳ cấu hình
    public List<SubjectDTO> mySubjects(Authentication auth) {
        Long teacherId = ((CustomUserDetails) auth.getPrincipal()).getId(); // bạn đã trả id trong CustomUserDetails
        List<TeacherSubject> ts = teacherSubjectRepo.findByTeacherId(teacherId);
        return ts.stream()
                .map(TeacherSubject::getSubject)
                .map(s -> new SubjectDTO(s.getId(), s.getName(), s.getCode(), s.getDepartment().getId()))
                .toList();
    }

    @GetMapping("/{id}/meta")
    public ResponseEntity<SubjectDTO> subjectMeta(@PathVariable Long id) {
        Subject s = subjectService.getSubjectById(id);
        return ResponseEntity.ok(subjectMapper.toDto(s));
    }
}