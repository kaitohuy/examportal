package com.exam.examserver.service.impl;

import com.exam.examserver.model.Authority;
import com.exam.examserver.model.user.CustomUserDetails;
import com.exam.examserver.model.user.User;
import com.exam.examserver.repo.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {
        User u = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found: " + username));

        // Map roles từ userRoles → GrantedAuthority
        Collection<GrantedAuthority> auths = u.getUserRoles().stream()
                .map(ur -> new SimpleGrantedAuthority(ur.getRole().getRoleName().name()))
                .collect(Collectors.toList());

        return new CustomUserDetails(
                u.getId(),
                u.getUsername(),
                u.getPassword(),
                u.isEnabled(),
                auths
        );
    }
}
