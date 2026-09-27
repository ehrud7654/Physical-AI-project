# 장치·스토리지 운영 상태

로그인 세션 동작은 변경하지 않는다. PostgreSQL은 장치·노드 등록 정보, 소유권,
학습 할당·lease의 기준이며, 최신 연결 상태와 용량은 Redis 관측값으로 판단한다.

| 키 | 내용 | 기본 TTL |
| --- | --- | --- |
| `{환경}:{앱}:device-online:{deviceId}` | 장치 ID, 마지막 heartbeat 수신 시각 | 45초 |
| `{환경}:{앱}:storage-node:{nodeId}` | worker ID, 상태, 전체·가용 용량, 업로드·학습 수, 수신 시각 | 45초 |

두 키는 `observedAt`, JSON `payload` 필드를 갖는 Redis Hash다. Lua로 값과 TTL을
원자적으로 갱신하며 오래된 요청이 최신 관측값을 덮어쓰지 않는다. PostgreSQL
트랜잭션 커밋 이후 게시하므로 롤백된 등록·heartbeat가 Redis에 반영되지 않는다.
Redis 게시 실패 시 503 `RUNTIME_STATE_UNAVAILABLE`을 반환한다. DB는 이미 커밋됐을
수 있으며 다음 heartbeat로 복구한다. Redis 장애 시 DB 스냅샷을 온라인으로 복원하지 않는다.

## PostgreSQL 스냅샷

- 기존 테이블과 컬럼을 유지한다. 추가 DB 마이그레이션은 필요하지 않다.
- 등록 및 이름 등 메타데이터 변경은 즉시 저장한다.
- heartbeat 관측값은 기본 60초 간격으로 조건부 UPDATE한다. 별도 주기 작업이 아니라
  다음 heartbeat가 간격을 넘겼을 때 갱신한다. 늦은 관측값은 무시한다.
- 명시적 스토리지 상태 변경과 장치 펌웨어·런타임 변경은 간격 이전에도 저장한다.
- DB `last_seen_at`, `available_bytes`, 연결 상태는 마지막 저장 관측값이다.
  Redis 장애·만료 시 현재 상태 대신 사용할 수 없다. 최근 60초 내 관측 기록은 유실될 수 있다.
- 장치·로봇·노드의 일반 메타데이터 수정은 변경된 컬럼만 UPDATE하여 오래된
  JPA 객체가 heartbeat 스냅샷을 덮어쓰지 않게 한다.

## 조회 및 실행

- 장치·로봇 목록은 PostgreSQL에서 가져오고 Redis 연결 상태를 합친다.
- Redis 키 만료/부재는 OFFLINE, 조회 실패는 UNKNOWN이다. UI는 UNKNOWN을
  `상태 확인 불가`로 표시한다. 대시보드 온라인/가용 수에는 확인된 온라인만 포함한다.
- 작업 검증, 로봇 실행, 대시보드도 Redis 상태를 사용한다.
- 기존 Jetson 오프라인 스케줄러는 Redis에서 OFFLINE을 확인한 경우에만 DB 관측 상태를 바꾼다.

## heartbeat

`POST /api/v1/devices/{deviceId}/heartbeat`는 장치 JWT의 deviceId 및 소유자를 검증한다.
기존 Jetson 요청은 그대로 `contractVersion=robot-command-v1`을 사용한다.
등록 완료한 UMI는 같은 endpoint에 `contractVersion=device-heartbeat-v1`,
`firmwareVersion`, `runtimeVersion`을 보내면 된다. 응답의 `nextHeartbeatSeconds`에
맞춰 주기적으로 호출해야 하며 UMI 클라이언트의 호출 구현은 별도로 필요하다.

스토리지 노드는 기존 `/internal/storage-nodes/register`,
`/internal/storage-nodes/{id}/heartbeat` 요청을 그대로 사용한다. 기존 GPU 스케줄러가
15초 간격으로 heartbeat를 보낸다. 이 전용 경로만 최신 상태·용량을 갱신한다.
학습 heartbeat는 스토리지를 임의로 ONLINE으로 바꾸거나 오래된 용량으로 TTL을 연장하지 않는다.

학습 할당은 Redis 상태·신선도·용량·보고된 학습 수와 DB 활성 작업 수를 확인한다.
상태 만료/오프라인/용량 부족은 기존 409 계열, Redis 장애는 503으로 신규 할당을 막는다.
할당 시 노드 DB 행 잠금을 사용하며 학습 할당·lease·GPU 중복 제약은 DB에 유지한다.
기존 학습의 heartbeat/lease 갱신은 Redis 스토리지 상태와 독립적으로 계속한다.
가용 용량은 관측값이며 디스크 용량을 예약하는 기능을 추가한 것은 아니다.

설정: `DEVICE_ONLINE_TTL`(45s), `STORAGE_NODE_STATE_TTL`(45s),
`RUNTIME_SNAPSHOT_INTERVAL`(60s). TTL은 heartbeat 간격보다 길어야 한다.
학습 할당에는 기존 `STORAGE_NODE_OFFLINE_AFTER` 신선도 제한도 적용한다.
