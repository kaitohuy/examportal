package com.exam.examserver.mapper;

import com.exam.examserver.dto.user.*;
import com.exam.examserver.enums.RoleType;
import com.exam.examserver.model.user.User;
import com.exam.examserver.model.user.UserRole;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface UserMapper {

    // Map Entity → Basic UserDTO (KHÔNG CÓ ROLES)
    @Named("mapToUserDto")
    UserDTO toDto(User user);

    @Named("mapToUserBasicDto")
    @Mapping(target = "id", source = "id")
    @Mapping(target = "username", source = "username")
    @Mapping(target = "firstName", source = "firstName")
    @Mapping(target = "lastName", source = "lastName")
    UserBasicDTO toDtoBasic(User user);

    // Map Entity → UserWithRolesDTO (CÓ ROLES)
    @Named("mapToUserWithRolesDto")
    @Mapping(target = "roles", expression = "java(userRolesToRoles(user.getUserRoles()))")
    UserWithRolesDTO toDtoWithRoles(User user);

    // Map CreateUserDTO → Entity
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userRoles", ignore = true)
    @Mapping(target = "profile", ignore = true)
    @Mapping(target = "enabled", ignore = true)
    User toEntity(CreateUserDTO dto);

    @Mapping(target = "roles", expression = "java(userRolesToRoles(user.getUserRoles()))")
    @Mapping(target = "department", expression = "java(getDepartmentBasic(user))")
    UserWithRolesAndDeptDTO toDtoWithDept(User user);

    // Helper: Chuyển Set<UserRole> → Set<RoleType>
    default Set<RoleType> userRolesToRoles(Set<UserRole> userRoles) {
        return userRoles.stream()
                .map(ur -> ur.getRole().getRoleName())
                .collect(Collectors.toSet());
    }

    // Helper lấy DepartmentBasicDTO từ teacherSubjects
    default DepartmentDTO getDepartmentBasic(User user) {
        return user.getTeacherSubjects().stream()
                // Lấy môn đầu tiên thầy dạy
                .map(ts -> ts.getSubject().getDepartment())
                .findFirst()
                .map(dept -> new DepartmentDTO(dept.getId(), dept.getName(), dept.getDescription(), toDtoBasic(user)))
                .orElse(null);
    }
}
