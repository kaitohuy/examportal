package com.exam.examserver.service.impl;

import com.exam.examserver.model.exam.Subject;
import com.exam.examserver.model.user.Department;
import com.exam.examserver.repo.DepartmentRepository;
import com.exam.examserver.repo.SubjectRepository;
import com.exam.examserver.service.SubjectService;
import jakarta.persistence.EntityNotFoundException;
import org.hibernate.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SubjectServiceImpl implements SubjectService {

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    public Subject createSubject(Subject subject) {
        // Validate và xử lý logic nghiệp vụ
        return subjectRepository.save(subject);
    }

    public Subject updateSubject(Long id, Subject subjectDetails) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Subject not found"));

        // Cập nhật trường
        subject.setName(subjectDetails.getName());
        subject.setCode(subjectDetails.getCode());

        return subjectRepository.save(subject);
    }

    public Subject getSubjectById(Long id) {
        return subjectRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Subject not found"));
    }

    // Phương thức đặc biệt để lấy cùng với giáo viên
    public Subject getSubjectByIdWithTeachers(Long id) {
        return subjectRepository.findByIdWithTeachers(id)
                .orElseThrow(() -> new EntityNotFoundException("Subject not found"));
    }

    @Override
    public List<Subject> getSubjectsByDepartmentId(Long departmentId) {
        // Có thể validate tồn tại department trước
        if (!departmentRepository.existsById(departmentId)) {
            throw new EntityNotFoundException("Department not found with id " + departmentId);
        }
        return subjectRepository.findByDepartmentId(departmentId);
    }

    public List<Subject> getAllSubjects() {
        return subjectRepository.findAll();
    }

    public void deleteSubject(Long id) {
        Subject subject = getSubjectById(id);
        subjectRepository.delete(subject);
    }
}