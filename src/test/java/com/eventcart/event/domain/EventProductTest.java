package com.eventcart.event.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eventcart.common.exception.BusinessException;
import com.eventcart.common.exception.ErrorCode;
import com.eventcart.product.domain.Product;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class EventProductTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 1, 12, 0);

    private EventProduct eventProduct(int stock, int perMemberLimit) {
        Event event = new Event("블랙프라이데이", NOW.minusHours(1), NOW.plusHours(1));
        return new EventProduct(event, new Product("에어팟", 300_000), 99_000, stock, perMemberLimit);
    }

    @Test
    void 구매하면_남은_수량이_줄어든다() {
        EventProduct ep = eventProduct(10, 3);

        ep.purchase(2, 0);

        assertThat(ep.getRemainingQuantity()).isEqualTo(8);
    }

    @Test
    void 재고보다_많이_사면_OUT_OF_STOCK() {
        EventProduct ep = eventProduct(1, 5);

        assertThatThrownBy(() -> ep.purchase(2, 0))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OUT_OF_STOCK));
        assertThat(ep.getRemainingQuantity()).isEqualTo(1);
    }

    @Test
    void 누적_구매가_1인_제한을_넘으면_PURCHASE_LIMIT_EXCEEDED() {
        EventProduct ep = eventProduct(10, 2);

        assertThatThrownBy(() -> ep.purchase(1, 2))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PURCHASE_LIMIT_EXCEEDED));
    }

    @Test
    void 복구는_총수량을_넘지_않는다() {
        EventProduct ep = eventProduct(5, 5);
        ep.purchase(2, 0);

        ep.restore(10);

        assertThat(ep.getRemainingQuantity()).isEqualTo(5);
    }

    @Test
    void 이벤트_기간_밖이면_EVENT_NOT_ONGOING() {
        EventProduct ep = eventProduct(5, 5);

        assertThatThrownBy(() -> ep.validateOngoing(NOW.plusHours(1)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EVENT_NOT_ONGOING));
    }
}
