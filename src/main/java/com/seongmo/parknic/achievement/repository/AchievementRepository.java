package com.seongmo.parknic.achievement.repository;

import com.seongmo.parknic.achievement.entity.Achievement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AchievementRepository extends JpaRepository<Achievement, String> {

    List<Achievement> findAllByOrderByThresholdAsc();
}
