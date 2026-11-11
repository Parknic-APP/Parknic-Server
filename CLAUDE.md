# CLAUDE.md — Parknic Server

## 역할: 이 저장소는 사용자가 직접 개발한다

- 서버 코드는 **사용자가 직접 작성한다.** Claude는 요청 없이 코드를 쓰거나 고치지 않는다.
- Claude가 하는 일: 질문에 설명, 코드 리뷰, 설계 상담, 앱이 기대하는 API 계약 알려주기. 코드 작성은 사용자가 명시적으로 부탁할 때만 한다.
- iOS 앱(`../fe/`)은 Claude가 개발한다. 앱과 서버의 약속은 `../fe/docs/api-spec.md`가 기준이다.
- Git: `Parknic-APP/Parknic-Server`, 작업은 브랜치 → PR로 `main`에 머지. 커밋 형식 `type: 제목`(한국어).

## 문서

- Notion [파크닉(Parknic)](https://app.notion.com/p/3fcfb019b7808005be0cd219e6cce288): 개발 순서는 "백엔드 개발 순서", 형식은 "API 명세", 구조·규칙은 "백엔드 설계". 사용자는 temp 서버의 개발 순서(0~10단계)를 따라 직접 구현한다.

## 현재 상태 (2026-11-09)

- Spring Boot 4.1.2, Java 21, JPA, Flyway, PostgreSQL + PostGIS, Lombok.
- 구현된 것: `common/error`(ErrorCode, BusinessException, GlobalExceptionHandler, ErrorResponse)와 테스트.
- 로컬 DB: `docker compose up -d`(postgis/postgis:16-3.4, 5432). 값은 `.env`(`.env.example` 참고). 기본 프로필 `local`, `application-local.yaml`은 Git 제외.
- 처음에는 로컬에서만 실행한다. 앱 Debug 빌드가 `http://localhost:8080`에 붙는다(포트 바꾸면 앱 `project.yml`도 바꿔야 함).

## 앱이 기대하는 것 (서버 만들 때 맞춰야 할 것)

전체는 `../fe/docs/api-spec.md`. 특히 놓치기 쉬운 것:

- 에러 응답은 `code` 문자열로 분기한다. 앱이 아는 코드: `CHECKIN_OUT_OF_RANGE`, `CHECKIN_ALREADY_IN_PROGRESS`, `BADGE_ALREADY_EARNED_TODAY`, `CHECKIN_TOO_EARLY`, `CHECKIN_EXPIRED_MIDNIGHT`, `REVIEW_BADGE_REQUIRED`, `REVIEW_ALREADY_EXISTS`, `NICKNAME_DUPLICATED`, `NICKNAME_INAPPROPRIATE`, `TOKEN_EXPIRED`. (현재 ErrorCode에 `NICKNAME_INAPPROPRIATE`가 없다.)
- 401이면 앱이 `POST /api/auth/reissue`로 재발급 후 1회 재시도한다. 재발급이 400/401/403이면 로그아웃.
- 로그인 `POST /api/auth/login/apple` `{ token, authorizationCode }` → `{ accessToken, refreshToken, isNewUser }`.
- 앱 Debug 빌드는 개발용 로그인을 쓴다: 같은 엔드포인트에 `token: "dev:local:simulator"`(`dev:<키>:<이름>`, 이 맥의 키는 비밀이 아닌 `local`). 로컬 서버는 `dev:`로 시작하면 마지막 `:` 뒤를 이름으로 받아 주면 된다(temp `DevAppleIdentityVerifier`와 같음). 이걸 받아야 시뮬레이터에서 로그인된다.
- 앱 Debug는 체크인을 **65초** 뒤 완료 요청한다. 실제 규칙은 20분이므로 로컬 프로필에서 완료 시간을 줄일 설정이 필요하다.
- 랭킹은 주간(월요일 0시 KST 초기화), 자정(KST)에 진행 중 체크인은 실패.

## 운영 중인 서버 (temp 서버, 건드릴 때 주의)

- 출시된 앱(1.0.0)은 아직 `~/code/parknic-temp/be`(Claude가 만든 임시 서버)를 쓴다: `https://parknic-dev.duckdns.org`.
- 호스트 `ubuntu@15.135.248.111`, 키 `~/.ssh/parknic-key.pem`, 앱 경로 `~/parknic`, `docker compose -f docker-compose.prod.yaml up -d`. SSH는 AWS 보안 그룹에 등록된 IP에서만 된다.
- `~/parknic/.env`의 `REVIEW_MODE`: 2026-11-09 심사 통과 후 `false`. **다음 버전 심사 제출 때 `true`로 켜고, 통과하면 끈다**(심사관이 한국 밖에서 체크인할 수 있게 심사용 공원을 노출).
- 사용자 서버가 운영으로 넘어가면 앱 Release `API_BASE_URL`, 약관·개인정보 URL(`/terms`, `/privacy`), 고객 지원(`/support`)도 함께 옮겨야 한다.
- temp 서버 코드는 API 동작을 확인하는 참고용이다. 사용자가 원하지 않으면 그대로 복사해 오지 않는다.
