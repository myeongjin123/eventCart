package com.eventcart.order.service;

import com.eventcart.common.exception.BusinessException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 주문 비즈니스 지표. Prometheus 이름: eventcart_orders_total, eventcart_orders_expired_total
 *
 * <ul>
 *   <li>result: success / sold_out / limit_exceeded / rejected(그 밖의 업무 거절) / error</li>
 *   <li>stage: 어디서 끝났나. gate(Redis에서 거절) / db(DB까지 감)
 *       → gate 비율이 높을수록 Redis가 DB 락 앞에서 요청을 많이 막아 준 것</li>
 * </ul>
 * 태그 값은 위의 고정 목록만 쓴다 (회원 id 같은 값을 넣으면 시계열이 폭증한다).
 */
@Component
public class OrderMetrics {

    private final MeterRegistry registry;
    private final Counter expired;

    public OrderMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.expired = Counter.builder("eventcart.orders.expired")
                .description("결제 기한이 지나 만료된 주문 수")
                .register(registry);
    }

    public void success() {
        record("success", "db");
    }

    public void failure(RuntimeException e, boolean rejectedAtGate) {
        record(resultOf(e), rejectedAtGate ? "gate" : "db");
    }

    public void expired(int count) {
        expired.increment(count);
    }

    private void record(String result, String stage) {
        Counter.builder("eventcart.orders")
                .description("주문 요청 결과")
                .tag("result", result)
                .tag("stage", stage)
                .register(registry)
                .increment();
    }

    private static String resultOf(RuntimeException e) {
        if (!(e instanceof BusinessException be)) {
            return "error";
        }
        return switch (be.getErrorCode()) {
            case OUT_OF_STOCK -> "sold_out";
            case PURCHASE_LIMIT_EXCEEDED -> "limit_exceeded";
            default -> "rejected";
        };
    }
}
