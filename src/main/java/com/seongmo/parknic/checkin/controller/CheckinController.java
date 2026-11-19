package com.seongmo.parknic.checkin.controller;

import com.seongmo.parknic.checkin.dto.CheckinSessionResponse;
import com.seongmo.parknic.checkin.dto.FailCheckinRequest;
import com.seongmo.parknic.checkin.dto.StartCheckinRequest;
import com.seongmo.parknic.checkin.service.CheckinService;
import com.seongmo.parknic.common.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/check-ins")
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService checkinService;

    @PostMapping
    public CheckinSessionResponse start(@CurrentUserId Long userId,
                                        @Valid @RequestBody StartCheckinRequest request) {
        return checkinService.start(userId, request);
    }

    @GetMapping("/current")
    public CheckinSessionResponse getCurrent(@CurrentUserId Long userId) {
        return checkinService.getCurrent(userId);
    }

    @PostMapping("/{sessionId}/cancel")
    public void cancel(@CurrentUserId Long userId, @PathVariable Long sessionId) {
        checkinService.cancel(userId, sessionId);
    }

    @PostMapping("/{sessionId}/fail")
    public void fail(@CurrentUserId Long userId, @PathVariable Long sessionId, @Valid @RequestBody FailCheckinRequest request) {
        checkinService.fail(userId, sessionId, request);
    }
}
