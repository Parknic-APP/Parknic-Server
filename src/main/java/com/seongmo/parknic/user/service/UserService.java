package com.seongmo.parknic.user.service;

import com.seongmo.parknic.auth.service.AuthService;
import com.seongmo.parknic.character.repository.CharacterRepository;
import com.seongmo.parknic.common.error.BusinessException;
import com.seongmo.parknic.common.error.ErrorCode;
import com.seongmo.parknic.common.time.Kst;
import com.seongmo.parknic.user.dto.NicknameCheckResponse;
import com.seongmo.parknic.user.dto.UpdateProfileRequest;
import com.seongmo.parknic.user.dto.UserProfileResponse;
import com.seongmo.parknic.user.entity.User;
import com.seongmo.parknic.user.entity.UserStatus;
import com.seongmo.parknic.user.repository.UserBlockRepository;
import com.seongmo.parknic.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String NICKNAME_UNIQUE_CONSTRAINT = "uq_users_nickname";

    private final UserRepository userRepository;
    private final CharacterRepository characterRepository;
    private final NicknameFilter nicknameFilter;
    private final AuthService authService;
    private final UserBlockRepository userBlockRepository;
    private final Clock clock;

    @Transactional
    public void updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findByIdAndStatusNot(userId, UserStatus.DELETED)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        if (nicknameFilter.isInappropriate(request.nickname())) {
            throw new BusinessException(ErrorCode.NICKNAME_INAPPROPRIATE);
        }

        if (userRepository.existsByNicknameAndIdNot(request.nickname(), userId)) {
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
        }
        user.updateProfile(request.nickname(), request.characterId());

        try {
            // 커밋을 기다리지 않고 UPDATE를 지금 보내서, 동시 요청이 만든 UNIQUE 위반을 이 안에서 잡도록 함
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            String reason = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
            if (reason != null && reason.contains(NICKNAME_UNIQUE_CONSTRAINT)) {
                throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getMe(Long userId) {
        User user = userRepository.findByIdAndStatusNot(userId, UserStatus.DELETED)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        String characterName = user.getCharacterId() == null
                ? null
                : characterRepository.findNameById(user.getCharacterId()).orElse(null);

        return new UserProfileResponse(
                user.getNickname(),
                user.getCharacterId(),
                characterName,
                user.getCreatedAt().atZone(Kst.ZONE).toOffsetDateTime()
        );
    }

    @Transactional(readOnly = true)
    public NicknameCheckResponse checkNickname(Long userId, String nickname) {
        if (nicknameFilter.isInappropriate(nickname)) {
            throw new BusinessException(ErrorCode.NICKNAME_INAPPROPRIATE);
        }

        boolean taken = userRepository.existsByNicknameAndIdNot(nickname, userId);
        return new NicknameCheckResponse(!taken);
    }

    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findByIdAndStatusNot(userId, UserStatus.DELETED)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        authService.logout(userId);
        user.withdraw();
    }

    @Transactional
    public void block(Long userId, Long targetId) {
        // 자신은 차단 불가
        if (userId.equals(targetId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        // 없는 사용자, 탈퇴한 사용자는 차단 불가
        if (!userRepository.existsByIdAndStatusNot(targetId, UserStatus.DELETED)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        userBlockRepository.insertIfAbsent(userId, targetId, clock.instant());
    }

    @Transactional
    public void unblock(Long userId, Long targetId) {
        userBlockRepository.delete(userId, targetId);
    }
}
