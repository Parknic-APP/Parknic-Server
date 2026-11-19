package com.seongmo.parknic.park.dto;

import java.util.List;

public record ParkListResponse(List<ParkSummaryResponse> parks) {
}
