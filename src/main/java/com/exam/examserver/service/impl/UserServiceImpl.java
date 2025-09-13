package com.exam.examserver.service.impl;

import com.exam.examserver.dto.user.*;
import com.exam.examserver.enums.RoleType;
import com.exam.examserver.enums.Status;
import com.exam.examserver.mapper.UserMapper;
import com.exam.examserver.model.user.Role;
import com.exam.examserver.model.user.User;
import com.exam.examserver.model.user.UserRole;
import com.exam.examserver.repo.*;
import com.exam.examserver.service.UserService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserMapper userMapper;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private TeacherSubjectRepository teacherSubjectRepository;

    @Override
    public UserDTO createUser(CreateUserDTO userDto) throws Exception {
        // Map DTO -> Entity
        User user = userMapper.toEntity(userDto);
        // default profile
        user.setProfile(null);

        // kiểm tra tồn tại
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new RuntimeException("Username already exists");
        }
        // mã hóa mật khẩu
        if (user.getPassword()==null || user.getPassword().trim().isEmpty()) {
            throw new RuntimeException("Password cannot be empty");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword().trim()));
        user.setStatus(Status.INACTIVE);

        // gán role NORMAL (roleId=2)
        Role role = roleRepository.findByRoleName(RoleType.TEACHER)
                .orElseGet(() -> roleRepository.save(new Role(2L, RoleType.TEACHER)));
        UserRole ur = new UserRole();
        ur.setUser(user);
        ur.setRole(role);
        Set<UserRole> urs = new HashSet<>();
        urs.add(ur);
        user.getUserRoles().addAll(urs);

        // lưu
        User saved = userRepository.save(user);
        // Map Entity -> DTO
        return userMapper.toDto(saved);
    }

    @Override
    public UserDTO getUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return userMapper.toDto(user);
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        departmentRepository.findByHeadUser_Id(userId).ifPresent(dept -> {
            dept.setHeadUser(null);
            departmentRepository.save(dept);
        });
        teacherSubjectRepository.deleteByTeacherId(userId);
        userRoleRepository.deleteByUserId(userId);
        userRepository.delete(user);
    }

    @Override
    public List<UserDTO> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(userMapper::toDto)
                .collect(Collectors.toList());
    }

    public UserStatisticsDTO getUserStatistics() {
        long totalUsers        = userRepository.count();
        long totalDepartments  = departmentRepository.count(); // NEW
        long totalTeachers     = userRepository.countByUserRoles_Role_RoleName(RoleType.TEACHER);
        long totalHeads        = userRepository.countByUserRoles_Role_RoleName(RoleType.HEAD); // nếu muốn

        UserStatisticsDTO dto = new UserStatisticsDTO();
        dto.setTotalUsers(totalUsers);
        dto.setTotalDepartments(totalDepartments);
        dto.setTotalTeachers(totalTeachers);
        dto.setTotalHeads(totalHeads);
        return dto;
    }

    // UserServiceImpl.java
    @Override
    public User findByIdOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
    }

    @Override
    public User updateUser(UserDTO dto) {
        User user = userRepository.findById(dto.getId())
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + dto.getId()));

        if (dto.getTeacherCode() != null) user.setTeacherCode(dto.getTeacherCode());
        if (dto.getUsername() != null) user.setUsername(dto.getUsername());
        if (dto.getFirstName() != null) user.setFirstName(dto.getFirstName());
        if (dto.getLastName() != null) user.setLastName(dto.getLastName());
        if (dto.getEmail() != null) user.setEmail(dto.getEmail());
        if (dto.getPhone() != null) user.setPhone(dto.getPhone());
        if (dto.getGender() != null) user.setGender(dto.getGender());
        if (dto.getBirthDate() != null) user.setBirthDate(dto.getBirthDate());
        if (dto.getProfile() != null) user.setProfile(dto.getProfile());
        if (dto.getStatus() != null) user.setStatus(dto.getStatus());
        // Enabled là boolean, nên cần xử lý riêng nếu cần

        return userRepository.save(user);
    }

    @Override
    public void updateUserRoles(Long userId, Set<RoleType> newRoles) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        userRoleRepository.deleteByUserId(userId); // xóa DB
        user.getUserRoles().clear();              // xóa trong bộ nhớ

        Set<UserRole> newUserRoles = new HashSet<>();
        if (newRoles != null && !newRoles.isEmpty()) {
            for (RoleType roleType : newRoles) {
                Role role = roleRepository.findByRoleName(roleType)
                        .orElseThrow(() -> new RuntimeException("Role not found: " + roleType));
                UserRole userRole = new UserRole();
                userRole.setUser(user);
                userRole.setRole(role);
                newUserRoles.add(userRole);
            }
        }

        user.getUserRoles().addAll(newUserRoles);
        userRepository.save(user);
    }

    @Override
    public void resetPassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        // Lưu ý mã hoá mật khẩu
        String encodedPassword = passwordEncoder.encode(newPassword);
        user.setPassword(encodedPassword);
        userRepository.save(user);
    }

    @Override
    public void toggleUserEnabled(Long id, boolean enabled) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setEnabled(enabled);
        userRepository.save(user);
    }

    @Override
    public void toggleUserEnabled(Long id, Boolean enabled) {

    }

    @Override
    public UserWithRolesAndDeptDTO buildUserWithRolesAndDeptDTO(String username) {
        System.out.println(">>> buildUserWithRolesAndDeptDTO username = " + username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + username));

        UserWithRolesAndDeptDTO dto = userMapper.toDtoWithDept(user);

        // In DTO ra console
        System.out.println(">>> DTO id = "          + dto.getId());
        System.out.println(">>> DTO username = "    + dto.getUsername());
        System.out.println(">>> DTO roles = "       + dto.getRoles());
        System.out.println(">>> DTO department = "  + dto.getDepartment());


        return dto;
    }

    @Override
    public List<UserWithRolesAndDeptDTO> getAllUsersWithRolesAndDept() {
        List<User> users = userRepository.findAll();
        return users.stream()
                .map(userMapper::toDtoWithDept)
                .collect(Collectors.toList());
    }

    public List<UserWithRolesDTO> getAllUsersWithRoles() {
        List<User> users = userRepository.findAll();
        return users.stream()
                .map(userMapper::toDtoWithRoles)
                .collect(Collectors.toList());
    }
}