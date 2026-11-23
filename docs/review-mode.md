# 심사용 공원(REVIEW_MODE) 운영 가이드

앱 심사관은 한국 밖에서 앱을 열어서 한국 공원 근처에 있을 수 없다. 그래서 심사 기간에만 **심사관 위치 근처의 심사용 공원**(`parks.review_only = true`)을 보이게 한다.

## 동작

| | `REVIEW_MODE=false` (평소, 기본값) | `REVIEW_MODE=true` (심사 기간) |
|---|---|---|
| 주변 공원 조회 | `review_only` 공원 제외 | 일반 공원과 `review_only` 공원 모두 포함 |
| 공원 상세 | `review_only` 공원은 404 | 정상 |
| 체크인 시작 | `review_only` 공원은 404 | 정상 (반경 판정 등 규칙은 그대로) |
| 리뷰 좋은 공원 | 제외 | 계속 제외 |

- 설정은 환경변수 `REVIEW_MODE`(설정 키 `parknic.review.mode`)이고, **서버를 재시작해야 반영**된다.
- `true`로 시작하면 서버 로그에 WARN(`REVIEW_MODE가 켜져 있습니다 ...`)이 남는다. 켜 둔 채 잊지 않도록 로그와 알림에서 확인한다.

## 심사용 공원 등록 (한 번만)

심사관이 있을 지역(보통 미국 쿠퍼티노 등) 좌표에 `review_only = true`인 공원을 넣는다. `manage_no`는 유일해야 한다. 반경(`radius_m`)은 체크인 판정에 쓰이므로 심사관이 실제로 머무는 위치를 포함하게 넉넉히 잡는다(예: 200m).

```sql
INSERT INTO parks (manage_no, name, park_type, address, latitude, longitude, area_m2, radius_m, review_only)
VALUES ('REVIEW-1', '심사용 공원', 'NEIGHBORHOOD', 'Cupertino, CA', 37.3349, -122.0090, 0, 200, true);
```

공원 CSV 적재(`parknic.park-import.file`)는 이미 있는 `review_only` 값을 덮어쓰지 않는다.

## 절차

1. 심사 제출 **전에** `REVIEW_MODE=true`로 바꾸고 서버를 재시작한다. (`docker-compose.prod.yaml`은 `.env`의 `REVIEW_MODE`를 읽는다)
2. 로그에 WARN이 있는지, 심사용 공원이 주변 조회에 나오는지 확인한다.
3. 심사 제출. 심사가 끝날 때까지 켜 둔다.
4. 심사 통과 후 `REVIEW_MODE=false`(또는 비움)로 바꾸고 재시작한다. 심사용 공원이 다시 숨겨지는지 확인한다.

## 주의

- 심사 중 심사관이 심사용 공원에서 받은 배지, 리뷰는 모드를 끈 뒤에도 DB에 남는다. 심사관도 일반 사용자이므로 정리하려면 해당 계정을 탈퇴 처리(데이터 삭제)한다.
- 심사용 공원 노출은 모든 사용자에게 적용된다. 심사가 길어지면 일반 사용자에게도 가짜 공원이 보이므로 기간을 짧게 둔다.
