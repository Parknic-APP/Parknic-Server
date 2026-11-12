package com.seongmo.parknic.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // ----- 계약: 앱이 code로 분기함, 문자열과 상태를 바꾸면 앱이 깨짐 -----
    CHECKIN_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "공원 반경 밖입니다."),
    CHECKIN_TOO_EARLY(HttpStatus.BAD_REQUEST, "아직 20분이 지나지 않았습니다."),
    CHECKIN_EXPIRED_MIDNIGHT(HttpStatus.BAD_REQUEST, "자정이 지나 체크인이 만료되었습니다."),
    CHECKIN_ALREADY_IN_PROGRESS(HttpStatus.CONFLICT, "이미 진행 중인 체크인이 있습니다."),
    BADGE_ALREADY_EARNED_TODAY(HttpStatus.CONFLICT, "오늘 이미 이 공원의 배지를 받았습니다."),
    REVIEW_BADGE_REQUIRED(HttpStatus.FORBIDDEN, "배지를 받은 공원에만 리뷰를 쓸 수 있습니다."),
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 리뷰를 작성했습니다."),
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    NICKNAME_INAPPROPRIATE(HttpStatus.CONFLICT, "사용할 수 없는 닉네임입니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다."),

    // ----- 공통 -----
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 Content-Type입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
