package com.exam.examserver.service.impl;

import com.exam.examserver.model.exam.Subject;
import com.exam.examserver.model.user.TeacherSubject;
import com.exam.examserver.model.user.User;
import com.exam.examserver.repo.SubjectRepository;
import com.exam.examserver.repo.TeacherSubjectRepository;
import com.exam.examserver.repo.UserRepository;
import com.exam.examserver.service.TeacherSubjectService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TeacherSubjectServiceImpl implements TeacherSubjectService {

    @Autowired
    private TeacherSubjectRepository teacherSubjectRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private UserRepository userRepository;

    public void assignTeacherToSubject(Long subjectId, Long teacherId) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new EntityNotFoundException("Subject not found"));

        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new EntityNotFoundException("Teacher not found"));

        // Kiểm tra xem phân công đã tồn tại chưa
        if (teacherSubjectRepository.existsBySubjectAndTeacher(subject, teacher)) {
            throw new IllegalStateException("Teacher already assigned to this subject");
        }

        TeacherSubject assignment = new TeacherSubject();
        assignment.setSubject(subject);
        assignment.setTeacher(teacher);

        teacherSubjectRepository.save(assignment);
    }

    public void removeTeacherFromSubject(Long subjectId, Long teacherId) {
        TeacherSubject assignment = teacherSubjectRepository.findBySubjectIdAndTeacherId(subjectId, teacherId)
                .orElseThrow(() -> new EntityNotFoundException("Assignment not found"));

        teacherSubjectRepository.delete(assignment);
    }
}