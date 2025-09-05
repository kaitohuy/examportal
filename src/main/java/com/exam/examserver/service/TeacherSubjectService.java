package com.exam.examserver.service;

import com.exam.examserver.model.user.TeacherSubject;

import java.util.List;

public interface TeacherSubjectService {

    void assignTeacherToSubject(Long subjectId, Long teacherId);

    void removeTeacherFromSubject(Long subjectId, Long teacherId);

}
