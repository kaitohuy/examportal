package com.exam.examserver.mapper;

import com.exam.examserver.dto.exam.SubjectDTO;
import com.exam.examserver.model.exam.Subject;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;


@Mapper(componentModel = "spring")
public interface SubjectMapper {

    @Mapping(source = "department.id",   target = "departmentId")
    @Mapping(source = "department.name", target = "departmentName") // NEW
    SubjectDTO toDto(Subject subject);

    @Mapping(target = "department", ignore = true) // optional
    Subject toEntity(SubjectDTO subjectDTO);

    List<SubjectDTO> toDtoList(List<Subject> subjects);
}
