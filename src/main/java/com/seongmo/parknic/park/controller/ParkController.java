package com.seongmo.parknic.park.controller;

import com.seongmo.parknic.park.dto.ParkDetailResponse;
import com.seongmo.parknic.park.dto.ParkListResponse;
import com.seongmo.parknic.park.service.ParkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/parks")
@RequiredArgsConstructor
public class ParkController {

    private final ParkService parkService;

    @GetMapping("/{parkId}")
    public ParkDetailResponse getPark(@PathVariable Long parkId) {
        return parkService.getPark(parkId);
    }

    @GetMapping("/nearby")
    public ParkListResponse getNearbyParks(@RequestParam double lat, @RequestParam double lng, @RequestParam(defaultValue = "1000") int radius) {
        return parkService.getNearbyParks(lat, lng, radius);
    }
}
