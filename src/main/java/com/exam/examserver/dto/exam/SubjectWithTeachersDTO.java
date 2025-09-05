package com.exam.examserver.dto.exam;

import com.exam.examserver.dto.user.UserBasicDTO;

import java.util.List;

public class SubjectWithTeachersDTO extends SubjectDTO {
    private List<UserBasicDTO> teachers;

    // getters, setters

    public List<UserBasicDTO> getTeachers() {
        return teachers;
    }

    public void setTeachers(List<UserBasicDTO> teachers) {
        this.teachers = teachers;
    }
}