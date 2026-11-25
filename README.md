# EventCart

[![CI](https://github.com/myeongjin123/eventcart/actions/workflows/ci.yml/badge.svg)](https://github.com/myeongjin123/eventcart/actions/workflows/ci.yml)

선착순 한정 수량 이벤트 쇼핑몰 백엔드. 동시 주문 상황에서 **초과 판매 0건, 1인 구매 제한 보장**을 목표로 한다.

- Java 21 · Spring Boot 3.5 · Spring Data JPA · Spring Security(JWT) · MySQL 8 · Redis · Gradle
- 테스트: JUnit 5 · Testcontainers(MySQL·Redis) · k6 부하 테스트 · GitHub Actions
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

## 빠른 시나리오 (curl)

로컬 실행 시 관리자 계정 `admin@eventcart.dev` / `admin1234!`가 자동 생성된다. (`ADMIN_EMAIL`, `ADMIN_PASSWORD`로 변경)

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
curl -s -XPOST localhost:8080/api/orders/checkout -H "$U"
```

> 1단계 DB를 쓰던 경우 `member` 테이블에 컬럼이 추가됐으므로 `docker compose down -v`로 초기화 후 실행한다.

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

## 다음 단계

로드맵은 [docs/DESIGN.md §8](docs/DESIGN.md#8-로드맵) 참고. 바로 다음은 **6단계: 결제 모의 연동**과 미결제 주문 자동 취소.
