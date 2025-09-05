package com.exam.examserver.model.user;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;


public class CustomUserDetails implements UserDetails, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String username;
    private final String password;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(Long id,
                             String username,
                             String password,
                             boolean enabled,
                             Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
        this.authorities = authorities;
    }
    // Thêm getter cho id
    public Long getId() {
        return id;
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override public String getPassword() {
        return password;
    }

    @Override public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; //khong het han
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;//khong bi khoa (nhap sai mk nhieu lan)
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;//mat khau khong het han(ko can thay doi dinh ky)
    }

    @Override public boolean isEnabled() {
        return enabled;
    }

}
