package com.seongmo.parknic.park.controller;

import com.seongmo.parknic.common.security.CurrentUserId;
import com.seongmo.parknic.park.dto.ParkDetailResponse;
import com.seongmo.parknic.park.dto.ParkListResponse;
import com.seongmo.parknic.park.dto.TopRatedParkListResponse;
import com.seongmo.parknic.park.service.ParkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/parks")
@RequiredArgsConstructor
public class ParkController {

    private final ParkService parkService;

    @GetMapping("/{parkId}")
    public ParkDetailResponse getPark(@CurrentUserId Long userId, @PathVariable Long parkId) {
        return parkService.getPark(userId, parkId);
    }

    @GetMapping("/nearby")
    public ParkListResponse getNearbyParks(@CurrentUserId Long userId, @RequestParam double lat, @RequestParam double lng, @RequestParam(defaultValue = "1000") int radius) {
        return parkService.getNearbyParks(userId, lat, lng, radius);
    }

    @GetMapping("/top-rated")
    public TopRatedParkListResponse getTopRatedParks(@CurrentUserId Long userId, @RequestParam double lat, @RequestParam double lng, @RequestParam(defaultValue = "5000") int radius) {
        return parkService.getTopRatedParks(userId, lat, lng, radius);
    }
}
