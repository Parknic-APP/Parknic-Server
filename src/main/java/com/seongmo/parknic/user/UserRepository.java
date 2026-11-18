package com.seongmo.parknic.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByProviderAndProviderId(String provider, String providerId);

    // 탈퇴하지 않은 사용자만 조회
    Optional<User> findByIdAndStatusNot(Long id, UserStatus status);

    boolean existsByNicknameAndIdNot(String nickname, Long userId);
}
