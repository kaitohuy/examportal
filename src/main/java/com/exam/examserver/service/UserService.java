package com.exam.examserver.service;

import com.exam.examserver.dto.user.*;
import com.exam.examserver.enums.RoleType;
import com.exam.examserver.model.user.User;

import java.util.List;
import java.util.Set;

public interface UserService {
    UserDTO createUser(CreateUserDTO dto) throws Exception;
    UserDTO getUser(String username);
    void deleteUser(Long userId);
    List<UserDTO> getAllUsers();
    UserStatisticsDTO getUserStatistics();
    void updateUserRoles(Long userId, Set<RoleType> roles);
    List<UserWithRolesDTO> getAllUsersWithRoles();
    List<UserWithRolesAndDeptDTO> getAllUsersWithRolesAndDept();
    User updateUser(UserDTO dto);

    void toggleUserEnabled(Long id, boolean enabled);

    void resetPassword(Long userId, String newPassword);

    void toggleUserEnabled(Long id, Boolean enabled);

    UserWithRolesAndDeptDTO buildUserWithRolesAndDeptDTO(String username);
}
