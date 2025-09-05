package com.exam.examserver.mapper;

import com.exam.examserver.dto.exam.SubjectWithTeachersDTO;
import com.exam.examserver.dto.user.UserBasicDTO;
import com.exam.examserver.model.exam.Subject;
import com.exam.examserver.model.user.TeacherSubject;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", uses = {UserMapper.class})
public interface SubjectWithTeachersMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "code", source = "code")
    @Mapping(target = "departmentId", source = "department.id")
    @Mapping(target = "teachers", source = "teacherSubjects", qualifiedByName = "mapTeacherSubjects")
    SubjectWithTeachersDTO toDto(Subject subject);

    @Named("mapTeacherSubjects")
    default List<UserBasicDTO> mapTeacherSubjects(Set<TeacherSubject> teacherSubjects) {
        if (teacherSubjects == null) return Collections.emptyList();

        return teacherSubjects.stream()
                .map(TeacherSubject::getTeacher)
                .filter(Objects::nonNull)
                .map(teacher -> {
                    UserBasicDTO dto = new UserBasicDTO();
                    dto.setId(teacher.getId());
                    dto.setFirstName(teacher.getFirstName());
                    dto.setLastName(teacher.getLastName());
                    return dto;
                })
                .collect(Collectors.toList());
    }
}