package com.seongmo.parknic.user;

import com.seongmo.parknic.common.security.CurrentUserId;
import com.seongmo.parknic.user.dto.UpdateProfileRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PatchMapping("/me")
    public void updateProfile(@CurrentUserId Long userId,
                              @Valid @RequestBody UpdateProfileRequest request) {
        userService.updateProfile(userId, request);
        log.info("프로필 수정 userId={}", userId);
    }
}
