package com.exam.examserver.controller;

import com.exam.examserver.dto.user.*;
import com.exam.examserver.model.user.User;
import com.exam.examserver.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/user")
@CrossOrigin("*")
public class UserController {

    @Autowired private UserService userService;

    @PostMapping("/")
    public ResponseEntity<UserDTO> createUser(@RequestBody CreateUserDTO userDto) throws Exception {
        UserDTO created = userService.createUser(userDto);
        return ResponseEntity.ok(created);
    }

    //update user
    @PutMapping("/")
    public ResponseEntity<User> update(@RequestBody UserDTO dto) {
        return ResponseEntity.ok(this.userService.updateUser(dto));
    }

    //update user role
    @PutMapping("/{id}/roles")
    public ResponseEntity<?> updateUserRoles(@PathVariable("id") Long userId, @RequestBody UpdateUserRolesDTO dto) {
        userService.updateUserRoles(userId, dto.getRoles());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@PathVariable Long id, @RequestBody ResetPasswordDTO dto) {
        System.out.println("Reset password cho userId: " + id +", newPass: " + dto.getNewPassword());
        userService.resetPassword(id, dto.getNewPassword());

        Map<String, String> response = new HashMap<>();
        response.put("message", "Password reset successfully");
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/toggle-enabled")
    public ResponseEntity<String> toggleUserEnabled(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        Boolean enabled = body.get("enabled");
        userService.toggleUserEnabled(id, enabled);
        return ResponseEntity.ok("User status updated");
    }

    @GetMapping("/with-dept")
    public ResponseEntity<List<UserWithRolesAndDeptDTO>> fetchUsersWithDept() {
        List<UserWithRolesAndDeptDTO> dtos = userService.getAllUsersWithRolesAndDept();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{username}")
    public ResponseEntity<UserDTO> getUser(@PathVariable String username) {
        return ResponseEntity.ok(userService.getUser(username));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/user-statistics")
    public UserStatisticsDTO getUserStatistics() {
        return userService.getUserStatistics();
    }

}

