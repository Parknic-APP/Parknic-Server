package com.seongmo.parknic.user.repository;

import com.seongmo.parknic.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByProviderAndProviderId(String provider, String providerId);

    boolean existsByNicknameAndIdNot(String nickname, Long userId);
}
