-- 탈퇴를 소프트 삭제(DELETED 상태)에서 실제 삭제로 바꾼다.
-- 이미 소프트 삭제된 사용자를 지운다. users를 참조하는 모든 외래키가 ON DELETE CASCADE라서
-- 배지, 체크인, 리뷰, 신고, 차단, 업적, 로그인 토큰도 함께 삭제된다.
DELETE FROM users WHERE status = 'DELETED';

ALTER TABLE users DROP CONSTRAINT ck_users_status;
ALTER TABLE users ADD CONSTRAINT ck_users_status CHECK (status IN ('PENDING', 'ACTIVE'));
