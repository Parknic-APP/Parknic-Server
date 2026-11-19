package com.seongmo.parknic.checkin.controller;

import com.seongmo.parknic.checkin.dto.CheckinSessionResponse;
import com.seongmo.parknic.checkin.dto.StartCheckinRequest;
import com.seongmo.parknic.checkin.service.CheckinService;
import com.seongmo.parknic.common.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
