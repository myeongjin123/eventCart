package com.eventcart.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eventcart.common.exception.BusinessException;
import com.eventcart.common.exception.ErrorCode;
import com.eventcart.order.dto.OrderDto;
import com.eventcart.order.service.OrderFacade;
import com.eventcart.order.stock.StockGate;
import com.eventcart.support.TestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Redis 재고 게이트와 DB가 어긋나지 않는지 (예약 → 실패 시 반납, 취소 시 반납) */
@SpringBootTest
@Import(TestFixture.class)
class StockGateTest {

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private StockGate stockGate;

    @Autowired
    private TestFixture fixture;

    @Test
    void 주문하면_Redis와_DB_재고가_같이_줄어든다() {
        Long member = fixture.member();
        Long ep = fixture.ongoingEventProduct(10, 3);

        orderFacade.orderDirect(member, new OrderDto.DirectRequest(ep, 2));

        assertThat(stockGate.remaining(ep)).isEqualTo(8);
        assertThat(fixture.remaining(ep)).isEqualTo(8);
    }

    @Test
    void 게이트는_통과했지만_DB에서_실패하면_Redis_예약을_돌려준다() {
        Long member = fixture.member();
        Long ep = fixture.endedEventProduct(10, 3); // 게이트는 기간을 모르고, DB가 막는다

        assertThatThrownBy(() -> orderFacade.orderDirect(member, new OrderDto.DirectRequest(ep, 2)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EVENT_NOT_ONGOING));

        assertThat(stockGate.remaining(ep)).isEqualTo(10);
        assertThat(fixture.remaining(ep)).isEqualTo(10);
    }

    @Test
    void 주문을_취소하면_Redis_재고와_1인_구매_수량도_돌아온다() {
        Long member = fixture.member();
        Long ep = fixture.ongoingEventProduct(10, 1);
        OrderDto.Response order = orderFacade.orderDirect(member, new OrderDto.DirectRequest(ep, 1));

        orderFacade.cancel(member, order.orderId());

        assertThat(stockGate.remaining(ep)).isEqualTo(10);
        // 1인 1개 제한인데 다시 살 수 있어야 한다 = Redis의 회원 구매 수량도 반납됨
        orderFacade.orderDirect(member, new OrderDto.DirectRequest(ep, 1));
        assertThat(stockGate.remaining(ep)).isEqualTo(9);
    }

    @Test
    void 품절이면_DB까지_가지_않고_게이트에서_거절한다() {
        Long first = fixture.member();
        Long second = fixture.member();
        Long ep = fixture.ongoingEventProduct(1, 1);
        orderFacade.orderDirect(first, new OrderDto.DirectRequest(ep, 1));

        assertThatThrownBy(() -> orderFacade.orderDirect(second, new OrderDto.DirectRequest(ep, 1)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OUT_OF_STOCK))
                .hasMessage("한정 상품 재고가 부족합니다. (남은 수량: 0개)");
        assertThat(stockGate.remaining(ep)).isZero();
    }

    @Test
    void Redis가_비워져도_DB_값으로_다시_초기화한다() {
        Long buyer = fixture.member();
        Long ep = fixture.ongoingEventProduct(5, 2);
        assertThat(stockGate.remaining(ep)).isNull(); // 아직 아무도 주문 안 함 = 키 없음

        orderFacade.orderDirect(buyer, new OrderDto.DirectRequest(ep, 2));
        stockGate.evict(ep); // Redis 재시작으로 키가 사라진 상황

        // 같은 회원이 또 사려 하면: DB에서 재고 3, 이 회원 구매 2개를 읽어 와 1인 제한으로 막아야 한다
        assertThatThrownBy(() -> orderFacade.orderDirect(buyer, new OrderDto.DirectRequest(ep, 1)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PURCHASE_LIMIT_EXCEEDED));
        assertThat(stockGate.remaining(ep)).isEqualTo(3);
    }
}
