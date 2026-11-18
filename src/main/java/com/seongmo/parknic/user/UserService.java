package com.seongmo.parknic.user;

import com.seongmo.parknic.common.error.BusinessException;
import com.seongmo.parknic.common.error.ErrorCode;
import com.seongmo.parknic.user.dto.UpdateProfileRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String NICKNAME_UNIQUE_CONSTRAINT = "uq_users_nickname";

    private final UserRepository userRepository;

    @Transactional
    public void updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findByIdAndStatusNot(userId, UserStatus.DELETED)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

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
}
