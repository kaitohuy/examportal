package com.exam.examserver.dto.user;

import com.exam.examserver.enums.RoleType;

import java.util.Set;

public class UserWithRolesDTO extends UserDTO {
    private Set<RoleType> roles;

    // Getter & Setter
    public Set<RoleType> getRoles() {
        return roles;
    }

    public void setRoles(Set<RoleType> roles) {
        this.roles = roles;
    }
}
