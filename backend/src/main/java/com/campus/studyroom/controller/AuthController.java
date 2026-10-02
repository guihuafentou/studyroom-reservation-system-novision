package com.campus.studyroom.controller;

import com.campus.studyroom.common.Result;
import com.campus.studyroom.dto.LoginDTO;
import com.campus.studyroom.dto.RegisterDTO;
import com.campus.studyroom.entity.User;
import com.campus.studyroom.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证接口
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Result<User> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.ok(authService.register(dto));
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginDTO dto) {
        return Result.ok(authService.login(dto));
    }

    @GetMapping("/me")
    public Result<User> me() {
        return Result.ok(authService.me());
    }
}
