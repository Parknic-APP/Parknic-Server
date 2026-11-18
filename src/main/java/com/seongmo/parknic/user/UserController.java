package com.seongmo.parknic.user;

import com.seongmo.parknic.common.security.CurrentUserId;
import com.seongmo.parknic.user.dto.NicknameCheckResponse;
import com.seongmo.parknic.user.dto.UpdateProfileRequest;
import com.seongmo.parknic.user.dto.UserProfileResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/me")
    public UserProfileResponse getMe(@CurrentUserId Long userId) {
        return userService.getMe(userId);
    }

    @GetMapping("/nickname/check")
    public NicknameCheckResponse checkNickname(
            @CurrentUserId Long userId,
            @RequestParam @Pattern(regexp = "^\\S{2,10}$") String nickname) {
        return userService.checkNickname(userId, nickname);
    }
}
