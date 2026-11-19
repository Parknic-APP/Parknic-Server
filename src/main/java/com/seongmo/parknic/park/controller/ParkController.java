package com.seongmo.parknic.park.controller;

import com.seongmo.parknic.park.dto.ParkDetailResponse;
import com.seongmo.parknic.park.service.ParkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/parks")
@RequiredArgsConstructor
public class ParkController {

    private final ParkService parkService;

    @GetMapping("/{parkId}")
    public ParkDetailResponse getPark(@PathVariable Long parkId) {
        return parkService.getPark(parkId);
    }
}
