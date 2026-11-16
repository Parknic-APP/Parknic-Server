package com.seongmo.parknic.auth;

import com.seongmo.parknic.auth.dto.LoginRequest;
import com.seongmo.parknic.auth.dto.LoginResponse;
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
}
