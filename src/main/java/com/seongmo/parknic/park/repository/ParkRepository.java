package com.seongmo.parknic.park.repository;

import com.seongmo.parknic.park.entity.Park;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkRepository extends JpaRepository<Park, Long> {
}
