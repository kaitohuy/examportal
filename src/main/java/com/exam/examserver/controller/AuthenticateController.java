package com.exam.examserver.controller;

import com.exam.examserver.config.JwtUtils;
import com.exam.examserver.dto.user.UserWithRolesAndDeptDTO;
import com.exam.examserver.enums.Status;
import com.exam.examserver.model.JwtRequest;
import com.exam.examserver.model.JwtResponse;
import com.exam.examserver.model.user.User;
import com.exam.examserver.repo.UserRepository;
import com.exam.examserver.service.UserService;
import com.exam.examserver.service.impl.UserDetailsServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Optional;

@RestController
@CrossOrigin("*")
public class AuthenticateController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserService userService;

    //generate token
    @PostMapping("/generate-token")
    public ResponseEntity<?> generateToken(@RequestBody JwtRequest jwtRequest) throws Exception{
        try {

            authenticate(jwtRequest.getUsername(), jwtRequest.getPassword());
        }catch (UsernameNotFoundException e) {
            e.printStackTrace();
            throw new Exception("User not found " + e.getMessage());
        }
        Optional<User> optionalUser = userRepository.findByUsername(jwtRequest.getUsername());
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            if (user.getStatus() == Status.LOCKED) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("account has been locked");
            }
            user.setStatus(Status.ACTIVE);
            userRepository.save(user);

            // authenticate
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(jwtRequest.getUsername());
            String token = this.jwtUtils.generateToken(userDetails);
            return ResponseEntity.ok(new JwtResponse(token));
        } else {
            // xử lý trường hợp không tìm thấy user
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid username");
        }
    }

    private void authenticate(String username, String password) throws Exception {
        System.out.println("Attempting to authenticate user: " + username);
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
            System.out.println("Authentication successful for user: " + username);
        } catch (DisabledException e) {
            System.out.println("User is disabled: " + username);
            throw new Exception("User disabled " + e.getMessage());
        } catch (BadCredentialsException e) {
            System.out.println("Invalid credentials for user: " + username);
            throw new Exception("Invalid Credential " + e.getMessage());
        }
    }

    @GetMapping("/current-user")
    public ResponseEntity<UserWithRolesAndDeptDTO> getCurrentUser(Authentication auth) {
        String username = auth.getName();
        UserWithRolesAndDeptDTO dto = userService.buildUserWithRolesAndDeptDTO(username);
        return ResponseEntity.ok(dto);
    }
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        // Lấy token từ header
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body("Missing token");
        }

        String token = header.substring(7);
        String username = jwtUtils.extractUsername(token); // Sử dụng jwtUtils
        Optional<User> optionalUser = userRepository.findByUsername(username);

        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            user.setStatus(Status.INACTIVE);
            userRepository.save(user);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body(Collections.singletonMap("message", "Logout successful"));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not found");
        }
    }

    @PostMapping("/logout-silent")
    public ResponseEntity<?> logoutSilent(@RequestParam("token") String token) {
        String username = jwtUtils.extractUsername(token);
        userRepository.findByUsername(username)
                .ifPresent(u -> {
                    u.setStatus(Status.INACTIVE);
                    userRepository.save(u);
                });
        return ResponseEntity.ok().build();
    }
}
