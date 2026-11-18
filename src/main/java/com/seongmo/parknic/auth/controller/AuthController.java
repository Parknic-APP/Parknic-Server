package com.seongmo.parknic.auth.controller;

import com.seongmo.parknic.auth.dto.ReissueRequest;
import com.seongmo.parknic.auth.dto.ReissueResponse;
import com.seongmo.parknic.auth.service.AuthService;
import com.seongmo.parknic.auth.dto.LoginRequest;
import com.seongmo.parknic.auth.dto.LoginResponse;
import com.seongmo.parknic.common.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login/apple")
    public LoginResponse loginApple(@Valid @RequestBody LoginRequest request) {
        return authService.loginApple(request);
    }

    @PostMapping("/reissue")
    public ReissueResponse reissue(@Valid @RequestBody ReissueRequest request) {
        return authService.reissue(request);
    }

    @PostMapping("/logout")
    public void logout(@CurrentUserId Long userId) {
        authService.logout(userId);
    }
}
