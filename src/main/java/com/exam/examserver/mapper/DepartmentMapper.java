package com.exam.examserver.mapper;

import com.exam.examserver.dto.user.DepartmentDTO;
import com.exam.examserver.model.user.Department;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = UserMapper.class)
public interface DepartmentMapper {

    @Mapping(target = "headUser", qualifiedByName = "mapToUserBasicDto")
    DepartmentDTO toDto(Department department);

    @Mapping(target = "id", ignore = true)
    Department toEntity(DepartmentDTO departmentDTO);

    List<DepartmentDTO> toDto(List<Department> departments);
}
