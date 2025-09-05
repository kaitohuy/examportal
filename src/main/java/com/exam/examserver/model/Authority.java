package com.exam.examserver.model;

import com.exam.examserver.enums.RoleType;
import org.springframework.security.core.GrantedAuthority;

public class Authority implements GrantedAuthority {

    private RoleType authority;

    public Authority(RoleType authority) {
        this.authority = authority;
    }

    @Override
    public String getAuthority() {
        // Trả về tên quyền với tiền tố "ROLE_" theo chuẩn Spring
        return "ROLE_" + authority.name();
    }
}
