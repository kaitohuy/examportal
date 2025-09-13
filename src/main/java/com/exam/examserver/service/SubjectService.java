package com.exam.examserver.service;

import com.exam.examserver.model.exam.Subject;

import java.util.List;
import java.util.Optional;

public interface SubjectService {
    Subject createSubject(Subject subject);
    Subject updateSubject(Long id, Subject subjectDetails);
    void deleteSubject(Long id);
    Subject getSubjectById(Long id);
    Subject getSubjectByIdWithTeachers(Long id);
    List<Subject> getSubjectsByDepartmentId(Long departmentId);
    List<Subject> getAllSubjects();
    List<Subject> getSubjectsByDepartmentIdWithTeachers(Long departmentId);
}

