package com.eventcart.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eventcart.common.exception.BusinessException;
import com.eventcart.common.exception.ErrorCode;
import com.eventcart.member.domain.Member;
import com.eventcart.member.domain.Role;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** 주문 상태 전이 규칙 (DB 없이) */
class OrderTest {

    private static final LocalDateTime ORDERED_AT = LocalDateTime.of(2026, 12, 1, 12, 0);
    private static final LocalDateTime DEADLINE = ORDERED_AT.plusMinutes(10);

    private Order newOrder() {
        return new Order(new Member("a@test.com", "tester", "hash", Role.USER), ORDERED_AT, DEADLINE);
    }

    @Test
    void 기한_안에_결제하면_PAID() {
        Order order = newOrder();

        order.markPaid(DEADLINE.minusSeconds(1));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void 기한이_지난_뒤_결제하면_PAYMENT_EXPIRED() {
        Order order = newOrder();

        assertThatThrownBy(() -> order.markPaid(DEADLINE.plusSeconds(1)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_EXPIRED));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void 만료는_결제_대기이고_기한이_지났을_때만() {
        Order beforeDeadline = newOrder();
        assertThat(beforeDeadline.expire(DEADLINE)).isFalse();

        Order paid = newOrder();
        paid.markPaid(ORDERED_AT.plusMinutes(1));
        assertThat(paid.expire(DEADLINE.plusMinutes(1))).isFalse();
        assertThat(paid.getStatus()).isEqualTo(OrderStatus.PAID);

        Order pending = newOrder();
        assertThat(pending.expire(DEADLINE.plusSeconds(1))).isTrue();
        assertThat(pending.getStatus()).isEqualTo(OrderStatus.EXPIRED);
    }

    @Test
    void 결제_완료_주문을_취소하면_환불이_필요하다고_알려준다() {
        Order pending = newOrder();
        assertThat(pending.cancel()).isFalse();

        Order paid = newOrder();
        paid.markPaid(ORDERED_AT.plusMinutes(1));
        assertThat(paid.cancel()).isTrue();
        assertThat(paid.getStatus()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    void 만료된_주문은_취소할_수_없다() {
        Order order = newOrder();
        order.expire(DEADLINE.plusSeconds(1));

        assertThatThrownBy(order::cancel)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_ORDER_STATUS));
    }
}
