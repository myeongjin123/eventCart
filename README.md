# EventCart

[![CI](https://github.com/myeongjin123/eventcart/actions/workflows/ci.yml/badge.svg)](https://github.com/myeongjin123/eventcart/actions/workflows/ci.yml)

선착순 한정 수량 이벤트 쇼핑몰 백엔드. 동시 주문 상황에서 **초과 판매 0건, 1인 구매 제한 보장**을 목표로 한다.

- Java 21 · Spring Boot 3.5 · Spring Data JPA · MySQL 8 · Gradle
- 설계 문서: [docs/DESIGN.md](docs/DESIGN.md) (요구사항, ERD, API, 동시성 전략, 로드맵)

## 실행

```bash
# 1) 테스트 (H2 인메모리, 외부 의존성 없음)
./gradlew test

# 2) MySQL로 실행
docker compose up -d
./gradlew bootRun

# 2') DB 없이 H2로 실행
./gradlew bootRun --args='--spring.profiles.active=h2'
```

## 빠른 시나리오 (curl)

```bash
H='Content-Type: application/json'
curl -s -XPOST localhost:8080/api/members  -H "$H" -d '{"email":"a@test.com","name":"홍길동"}'
curl -s -XPOST localhost:8080/api/products -H "$H" -d '{"name":"에어팟 프로","price":359000}'
curl -s -XPOST localhost:8080/api/events   -H "$H" \
  -d '{"name":"블랙프라이데이","startAt":"2026-11-01T00:00:00","endAt":"2026-12-31T23:59:59"}'
curl -s -XPOST localhost:8080/api/events/1/products -H "$H" \
  -d '{"productId":1,"eventPrice":99000,"totalQuantity":10,"perMemberLimit":1}'

curl -s localhost:8080/api/events/ongoing
curl -s -XPOST localhost:8080/api/cart/items -H "$H" -H 'X-Member-Id: 1' -d '{"eventProductId":1,"quantity":1}'
curl -s -XPOST localhost:8080/api/orders/checkout -H 'X-Member-Id: 1'
curl -s -XPOST localhost:8080/api/orders/1/cancel  -H 'X-Member-Id: 1'
```

## 동시성 처리 요약

`OrderService.placeOrder`

1. 주문할 이벤트 상품 행을 **id 오름차순 `SELECT … FOR UPDATE`** (데드락 방지)
2. 락을 쥔 채 기간 → 회원 누적 구매 수량(1인 제한) → 재고 순으로 검증 후 차감
3. 주문 트랜잭션은 **READ_COMMITTED** — MySQL 기본 REPEATABLE_READ에서는 락 대기 후 읽는 누적 구매 수량이 오래된 스냅샷이라 1인 제한이 뚫릴 수 있음
4. 장바구니 주문 시 `EventProduct` 엔티티를 미리 로딩하지 않음 — 1차 캐시에 있으면 `FOR UPDATE` 조회가 오래된 재고 값을 돌려줄 수 있음

검증: `OrderConcurrencyTest`
- 재고 10개 / 100명 동시 주문 → 성공 10, `OUT_OF_STOCK` 90, 잔여 0
- 1인 제한 2개 / 같은 회원 20회 동시 주문 → 성공 2, `PURCHASE_LIMIT_EXCEEDED` 18

## 다음 단계

로드맵은 [docs/DESIGN.md §8](docs/DESIGN.md#8-로드맵) 참고. 바로 다음은 **2단계: Spring Security + JWT** 로 `X-Member-Id` 헤더 제거.
