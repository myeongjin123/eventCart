# EventCart

[![CI](https://github.com/myeongjin123/eventcart/actions/workflows/ci.yml/badge.svg)](https://github.com/myeongjin123/eventcart/actions/workflows/ci.yml)

선착순 한정 수량 이벤트 쇼핑몰 백엔드. 동시 주문 상황에서 **초과 판매 0건, 1인 구매 제한 보장**을 목표로 한다.

- Java 21 · Spring Boot 3.5 · Spring Data JPA · Spring Security(JWT) · MySQL 8 · Redis · Gradle
- 테스트: JUnit 5 · Testcontainers(MySQL·Redis) · k6 부하 테스트 · GitHub Actions
- 배포·운영: Docker · docker compose · GHCR · Actuator · Prometheus · Grafana
- 설계 문서: [docs/DESIGN.md](docs/DESIGN.md) (요구사항, ERD, API, 동시성 전략, 로드맵)

## 실행

```bash
# 1) 테스트 (Docker만 켜져 있으면 됨: MySQL 8·Redis 컨테이너를 테스트가 직접 띄움)
./gradlew test

# 2) MySQL로 실행
docker compose up -d
./gradlew bootRun

# 2') MySQL 없이 H2로 실행 (Redis는 필요)
./gradlew bootRun --args='--spring.profiles.active=h2'
```

## 배포 (Docker, 서버 한 대)

```bash
cp deploy/.env.example deploy/.env      # 비밀번호, JWT_SECRET(openssl rand -base64 48) 채우기
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build
```

| 서비스 | 주소 | 공개 |
|---|---|---|
| API | `:8080` | 외부 |
| Grafana | `:3000` (admin / `GRAFANA_ADMIN_PASSWORD`) | 외부 |
| Prometheus | `127.0.0.1:9090` | 서버 안에서만 |
| 지표·헬스체크 | `app:8081/actuator/prometheus`, `/actuator/health` | 내부망만 |
| MySQL, Redis | - | 내부망만 |

- `main`에 합쳐지면 이미지가 `ghcr.io/myeongjin123/eventcart:latest`(비공개)로 올라간다. 서버에서는 `.env`의 `APP_IMAGE`에 넣고 `docker compose pull && docker compose up -d`.
- PR마다 **Deploy check** 워크플로가 이 스택을 러너에서 그대로 띄워 부하 → 지표 수집 → 대시보드까지 확인한다.

## 모니터링 (Grafana 대시보드 "EventCart")

- 주문 결과(건/초): 성공·품절·1인 제한 초과, **Redis에서 막았는지(gate) / DB까지 갔는지(db)**
- 주문 API 응답 시간 p50·p95·p99
- DB 커넥션: 사용 중 / **대기 중** (DB 락 병목이 여기 보인다)
- HTTP 상태별 요청, JVM 힙, CPU, 만료 주문 수

## 빠른 시나리오 (curl)

로컬 실행 시 관리자 계정 `admin@eventcart.dev` / `admin1234!`가 자동 생성된다. (`ADMIN_EMAIL`, `ADMIN_PASSWORD`로 변경)

> **개발용 기본값 안내** · 저장소에 보이는 비밀번호와 키(`admin1234!`, `application.yml`의 JWT 키, `docker-compose.yml`의 DB 비밀번호, 테스트용 키)는 **로컬 개발·테스트 전용**이다. 배포용 `prod` 프로필에는 기본값이 없고, 모든 비밀값을 `deploy/.env`(git 제외) → 환경변수로만 받는다. JWT_SECRET이 없으면 앱이 시작되지 않는다.

```bash
H='Content-Type: application/json'
login() { curl -s -XPOST localhost:8080/api/auth/login -H "$H" -d "{\"email\":\"$1\",\"password\":\"$2\"}" | sed 's/.*"accessToken":"\([^"]*\)".*/\1/'; }

# 관리자: 상품·이벤트 등록
ADMIN=$(login admin@eventcart.dev 'admin1234!')
A="Authorization: Bearer $ADMIN"
curl -s -XPOST localhost:8080/api/products -H "$H" -H "$A" -d '{"name":"에어팟 프로","price":359000}'
curl -s -XPOST localhost:8080/api/events   -H "$H" -H "$A" \
  -d '{"name":"블랙프라이데이","startAt":"2026-11-01T00:00:00","endAt":"2026-12-31T23:59:59"}'
curl -s -XPOST localhost:8080/api/events/1/products -H "$H" -H "$A" \
  -d '{"productId":1,"eventPrice":99000,"totalQuantity":10,"perMemberLimit":1}'

# 회원: 가입 → 로그인 → 장바구니 → 주문
curl -s -XPOST localhost:8080/api/members -H "$H" -d '{"email":"a@test.com","name":"홍길동","password":"password123"}'
U="Authorization: Bearer $(login a@test.com password123)"
curl -s localhost:8080/api/events/ongoing
curl -s -XPOST localhost:8080/api/cart/items -H "$H" -H "$U" -d '{"eventProductId":1,"quantity":1}'
curl -s -XPOST localhost:8080/api/orders/checkout -H "$U"          # → PENDING_PAYMENT, 10분 안에 결제
curl -s -XPOST localhost:8080/api/orders/1/payment -H "$H" -H "$U" -d '{"method":"CARD"}'   # → PAID
# "DECLINED_CARD"로 결제하면 거절(FAILED)되고 주문은 결제 대기로 남는다
```

> 이전 단계 DB를 쓰던 경우 테이블 구조와 주문 상태 값이 바뀌었으므로 `docker compose down -v`로 초기화 후 실행한다.

## 인증·권한 (2단계)

| 구분 | API | 권한 |
|---|---|---|
| 누구나 | `POST /api/members`, `POST /api/auth/login`, `GET /api/products/**`, `GET /api/events/**` | - |
| 관리자 | `POST /api/products`, `POST /api/events`, `POST /api/events/{id}/products` | `ADMIN` |
| 로그인 | 장바구니, 주문 | `USER` / `ADMIN` |

- 로그인 성공 시 JWT 액세스 토큰(1시간) 발급 → `Authorization: Bearer <토큰>`
- 비밀번호는 BCrypt 해시로 저장, 응답에 포함하지 않음
- 서명 키는 환경변수 `JWT_SECRET`(Base64, 32바이트 이상)으로 주입
- 토큰 없음·위조·만료 → `401 UNAUTHORIZED`, 권한 부족 → `403 FORBIDDEN`

## 주문·결제 흐름 (6단계)

```
주문 → PENDING_PAYMENT (재고 차감, 결제 기한 10분)
        ├─ 결제 승인 → PAID ─ 취소 → CANCELED (환불 + 재고 반납)
        ├─ 취소 → CANCELED (재고 반납)
        └─ 기한 지남 → EXPIRED (스케줄러가 30초마다, 재고 반납)
```

결제·취소·만료는 주문 행 락 + 상태 재확인으로 처리해, 결제와 만료가 동시에 와도 한쪽만 반영된다.

## 동시성 처리 요약

4단계부터 주문은 `OrderFacade`를 거친다: **Redis에서 재고·1인 제한 예약 → DB 주문 → 실패 시 예약 반납**. 품절 요청은 DB에 가지 않는다. DB는 아래 방식으로 최종 검증한다.

`OrderService.placeOrder`

1. 주문할 이벤트 상품 행을 **id 오름차순 `SELECT … FOR UPDATE`** (데드락 방지)
2. 락을 쥔 채 기간 → 회원 누적 구매 수량(1인 제한) → 재고 순으로 검증 후 차감
3. 주문 트랜잭션은 **READ_COMMITTED** — MySQL 기본 REPEATABLE_READ에서는 락 대기 후 읽는 누적 구매 수량이 오래된 스냅샷이라 1인 제한이 뚫릴 수 있음
4. 장바구니 주문 시 `EventProduct` 엔티티를 미리 로딩하지 않음 — 1차 캐시에 있으면 `FOR UPDATE` 조회가 오래된 재고 값을 돌려줄 수 있음

검증: `OrderConcurrencyTest`
- 재고 10개 / 100명 동시 주문 → 성공 10, `OUT_OF_STOCK` 90, 잔여 0
- 1인 제한 2개 / 같은 회원 20회 동시 주문 → 성공 2, `PURCHASE_LIMIT_EXCEEDED` 18

## 부하 테스트

회원 500명 · VU 100 · 재고 100개 · 인기 상품 1개 집중 (GitHub Actions 2코어 러너, 2회 범위)

| 지표 | 3단계 (DB 락만) | 4단계 (+ Redis 게이트) |
|---|---|---|
| 처리량 | 110~126 건/초 | 191~303 건/초 |
| 전체 응답 p50 | 617~713 ms | 36~66 ms |
| 품절 안내 p50 | 락 대기 | 31~55 ms |
| 초과 판매 | 0 | 0 |

자세한 조건과 해석: [docs/LOAD_TEST.md](docs/LOAD_TEST.md)

## 로드맵

1~7단계 완료. 남은 개선 과제는 [docs/DESIGN.md §10](docs/DESIGN.md#10-로드맵) 참고.
