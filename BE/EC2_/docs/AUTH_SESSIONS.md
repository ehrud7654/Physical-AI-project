# Redis 로그인 세션

- `POST /api/v1/auth/signup`: `{ "name": "이름", "email": "user@example.com", "password": "..." }`로 가입한다.
  성공하면 201과 `data: {id, email, name}`을 반환한다. 비밀번호는 BCrypt로 저장하며 자동 로그인하지 않는다.
- `GET /api/v1/auth/email-availability?email=user@example.com`: 인증 없이 이메일 중복을 확인한다.
  응답은 `data: {email, available}`이며 캐시하지 않는다. 입력 오류는 422, 가입 시 이메일 중복은 409 `EMAIL_ALREADY_EXISTS`다.
- 이메일은 공백 제거 및 소문자로 정규화한다. 기존 V1의 `uq_users_email_lower` UNIQUE 인덱스를 활용하며
  동시에 가입하더라도 중복 저장을 차단한다. 중복 확인 결과가 가입 시점까지 예약되는 것은 아니다.
- 프론트는 이메일 형식을 검증한 후 입력이 400ms 멈추면 중복을 확인한다. 이전 입력의 요청은 취소하며
  현재 이메일의 사용 가능 여부가 확인되어야 가입할 수 있다. 비밀번호는 8자 이상, UTF-8 기준 72바이트 이하이다.

- `POST /api/v1/auth/login`: 기존 이메일/비밀번호 요청과 응답 형식을 유지한다.
  로그인마다 UUID 세션을 만들고 JWT에 `sid`를 넣는다. Redis 세션은
  `{환경}:{앱이름}:session:{sid}`에 저장하며 만료 시각은 JWT `exp`와 같다.
  저장은 TTL을 포함하는 단일 Redis SET 명령을 사용한다.
- 사용자 요청: JWT 서명/발급자/시간 검증 후 Redis 세션의 존재, 사용자 ID,
  세션 ID, 만료, 폐기 여부를 검사한다. 실패하면 401이다.
- `POST /api/v1/auth/logout`: Bearer 토큰의 현재 세션을 삭제하고 204를 반환한다.
  다른 로그인 세션은 유지된다. 삭제된 토큰으로 다시 요청하면 401이다.
- 로그인, 세션 검사, 로그아웃에서 Redis 장애가 발생하면
  `503 {"code":"AUTH_SESSION_UNAVAILABLE", "message":"..."}`를 반환한다.
  Redis 없이 사용자 인증을 허용하는 fallback은 없다.
- `actorType=DEVICE`, `scope=device` 토큰은 사용자 세션을 요구하지 않는다.
  장치 토큰으로 사용자 로그아웃 API를 호출하면 403이다.
- 프론트엔드는 서버 로그아웃 성공 후 로컬 저장 정보를 삭제한다.
  네트워크/503 실패 시 로그인 상태를 유지하고 재시도 안내를 표시한다.
  401은 기존 세션 만료 처리로 정리한다. 자동 세션 정리는 서버 로그아웃을 호출하지 않는다.

배포 후 기존 `sid` 없는 사용자 토큰은 거부되므로 재로그인이 필요하다.
Redis 세션이 유실되거나 TTL로 삭제되어도 재로그인이 필요하다.
로그아웃은 이후 인증 요청을 차단하며 이미 인증을 통과한 요청이나 열린 스트림을 취소하지 않는다.

검증: `AuthSessionIntegrationTest`는 실제 DB와 Redis로 로그인/TTL/로그아웃/세션 분리,
잘못된 세션, 기존 토큰, 장치 토큰을 확인한다. Redis 장애는 저장소 spy로 주입한다.
테스트 DB/Redis 연결은 기존 `SharedTestContainers`의 TEST_POSTGRES_*와 TEST_REDIS_*를 사용한다.
프론트엔드 `tests/authService.test.ts`는 서버 호출 순서, 성공 후 정리, 장애 후 상태 유지,
로컬 정리의 네트워크 미호출을 확인한다.
