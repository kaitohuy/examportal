package com.exam.examserver.controller;

import com.exam.examserver.service.impl.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@CrossOrigin("*")
@RequestMapping("/auth")
public class PasswordResetController {
    @Autowired
    PasswordResetService svc;

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgot(@RequestBody Map<String,String> body, HttpServletRequest req){
        var email = body.getOrDefault("email", "");
        var appUrl = (req.isSecure() ? "https://" : "http://") + req.getServerName()
                + (req.getServerPort()==80||req.getServerPort()==443? "": ":"+req.getServerPort());
        svc.createAndSendToken(email, appUrl);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> reset(@RequestBody Map<String,String> body){
        svc.resetPassword(body.get("token"), body.get("newPassword"));
        return ResponseEntity.ok().build();
    }
}

