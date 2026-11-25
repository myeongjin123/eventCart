package com.eventcart.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eventcart.cart.dto.CartDto;
import com.eventcart.cart.service.CartService;
import com.eventcart.common.exception.BusinessException;
import com.eventcart.common.exception.ErrorCode;
import com.eventcart.order.domain.OrderStatus;
import com.eventcart.order.dto.OrderDto;
import com.eventcart.order.service.OrderFacade;
import com.eventcart.support.IntegrationTest;
import com.eventcart.support.TestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class CartOrderFlowTest {

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private TestFixture fixture;

    @Test
    void 장바구니_담기_주문_취소_흐름() {
        Long member = fixture.member();
        Long ep1 = fixture.ongoingEventProduct(10, 3);
        Long ep2 = fixture.ongoingEventProduct(10, 3);

        cartService.add(member, new CartDto.AddRequest(ep1, 1));
        cartService.add(member, new CartDto.AddRequest(ep1, 1)); // 같은 상품 → 수량 합산
        cartService.add(member, new CartDto.AddRequest(ep2, 3));

        CartDto.Response cart = cartService.getCart(member);
        assertThat(cart.items()).hasSize(2);
        assertThat(cart.totalPrice()).isEqualTo(5_000 * 5);

        OrderDto.Response order = orderFacade.checkout(member);
        assertThat(order.status()).isEqualTo(OrderStatus.ORDERED);
        assertThat(order.totalPrice()).isEqualTo(25_000);
        assertThat(fixture.remaining(ep1)).isEqualTo(8);
        assertThat(fixture.remaining(ep2)).isEqualTo(7);
        assertThat(cartService.getCart(member).items()).isEmpty();

        OrderDto.Response canceled = orderFacade.cancel(member, order.orderId());
        assertThat(canceled.status()).isEqualTo(OrderStatus.CANCELED);
        assertThat(fixture.remaining(ep1)).isEqualTo(10);
        assertThat(fixture.remaining(ep2)).isEqualTo(10);

        assertErrorCode(() -> orderFacade.cancel(member, order.orderId()), ErrorCode.INVALID_ORDER_STATUS);
    }

    @Test
    void 장바구니_전체_주문은_하나라도_실패하면_전부_롤백된다() {
        Long member = fixture.member();
        Long enough = fixture.ongoingEventProduct(10, 5);
        Long scarce = fixture.ongoingEventProduct(1, 5);
        cartService.add(member, new CartDto.AddRequest(enough, 2));
        cartService.add(member, new CartDto.AddRequest(scarce, 2));

        assertThatThrownBy(() -> orderFacade.checkout(member))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OUT_OF_STOCK))
                .hasMessageContaining("재고가 부족합니다. (남은 수량: 1개)");

        assertThat(fixture.remaining(enough)).isEqualTo(10);
        assertThat(fixture.remaining(scarce)).isEqualTo(1);
        assertThat(cartService.getCart(member).items()).hasSize(2);
    }

    @Test
    void 취소한_수량은_1인_제한에서_빠진다() {
        Long member = fixture.member();
        Long ep = fixture.ongoingEventProduct(10, 1);

        OrderDto.Response first = orderFacade.orderDirect(member, new OrderDto.DirectRequest(ep, 1));
        assertErrorCode(() -> orderFacade.orderDirect(member, new OrderDto.DirectRequest(ep, 1)),
                ErrorCode.PURCHASE_LIMIT_EXCEEDED);

        orderFacade.cancel(member, first.orderId());
        OrderDto.Response second = orderFacade.orderDirect(member, new OrderDto.DirectRequest(ep, 1));

        assertThat(second.status()).isEqualTo(OrderStatus.ORDERED);
        assertThat(fixture.remaining(ep)).isEqualTo(9);
    }

    @Test
    void 빈_장바구니는_주문할_수_없다() {
        Long member = fixture.member();

        assertErrorCode(() -> orderFacade.checkout(member), ErrorCode.CART_EMPTY);
    }

    private void assertErrorCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }
}
