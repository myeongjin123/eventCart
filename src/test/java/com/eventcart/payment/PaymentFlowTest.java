package com.eventcart.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eventcart.common.exception.BusinessException;
import com.eventcart.common.exception.ErrorCode;
import com.eventcart.order.domain.OrderStatus;
import com.eventcart.order.dto.OrderDto;
import com.eventcart.order.service.OrderFacade;
import com.eventcart.order.service.OrderService;
import com.eventcart.order.stock.StockGate;
import com.eventcart.payment.domain.Payment;
import com.eventcart.payment.domain.PaymentStatus;
import com.eventcart.payment.dto.PaymentDto;
import com.eventcart.payment.gateway.FakePaymentGateway;
import com.eventcart.payment.repository.PaymentRepository;
import com.eventcart.payment.service.PaymentService;
import com.eventcart.support.IntegrationTest;
import com.eventcart.support.TestFixture;
import java.time.LocalDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class PaymentFlowTest {

    private static final PaymentDto.PayRequest CARD = new PaymentDto.PayRequest("CARD");
    private static final PaymentDto.PayRequest DECLINED = new PaymentDto.PayRequest(FakePaymentGateway.DECLINED_METHOD);

    @Autowired private OrderFacade orderFacade;
    @Autowired private OrderService orderService;
    @Autowired private PaymentService paymentService;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private StockGate stockGate;
    @Autowired private TestFixture fixture;

    @Test
    void 주문하면_결제_대기이고_결제하면_PAID가_된다() {
        Long member = fixture.member();
        OrderDto.Response order = orderFacade.orderDirect(member, direct(fixture.ongoingEventProduct(10, 1)));
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.paymentDeadline()).isEqualTo(order.orderedAt().plusMinutes(10));

        PaymentDto.Response paid = paymentService.pay(member, order.orderId(), CARD);

        assertThat(paid.paymentStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(paid.orderStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paid.amount()).isEqualTo(order.totalPrice());
        assertThat(statusOf(member, order)).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void 카드가_거절되면_결제_대기로_남고_다시_결제할_수_있다() {
        Long member = fixture.member();
        OrderDto.Response order = orderFacade.orderDirect(member, direct(fixture.ongoingEventProduct(10, 1)));

        PaymentDto.Response declined = paymentService.pay(member, order.orderId(), DECLINED);
        assertThat(declined.paymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(declined.orderStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(declined.failureReason()).contains("거절");

        PaymentDto.Response retried = paymentService.pay(member, order.orderId(), CARD);
        assertThat(retried.orderStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paymentRepository.findAllByOrderIdOrderByIdAsc(order.orderId()))
                .extracting(Payment::getStatus)
                .containsExactly(PaymentStatus.FAILED, PaymentStatus.APPROVED);
    }

    @Test
    void 이미_결제한_주문은_다시_결제할_수_없다() {
        Long member = fixture.member();
        OrderDto.Response order = orderFacade.orderDirect(member, direct(fixture.ongoingEventProduct(10, 1)));
        paymentService.pay(member, order.orderId(), CARD);

        assertErrorCode(() -> paymentService.pay(member, order.orderId(), CARD), ErrorCode.INVALID_ORDER_STATUS);
    }

    @Test
    void 남의_주문은_결제할_수_없다() {
        Long owner = fixture.member();
        Long other = fixture.member();
        OrderDto.Response order = orderFacade.orderDirect(owner, direct(fixture.ongoingEventProduct(10, 1)));

        assertErrorCode(() -> paymentService.pay(other, order.orderId(), CARD), ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    void 결제_기한이_지나면_만료되고_재고와_1인_제한이_돌아온다() {
        Long member = fixture.member();
        Long ep = fixture.ongoingEventProduct(10, 1);
        OrderDto.Response order = orderFacade.orderDirect(member, direct(ep));
        assertThat(fixture.remaining(ep)).isEqualTo(9);

        expireAll(order.paymentDeadline().plusSeconds(1));

        assertThat(statusOf(member, order)).isEqualTo(OrderStatus.EXPIRED);
        assertThat(fixture.remaining(ep)).isEqualTo(10);       // DB
        assertThat(stockGate.remaining(ep)).isEqualTo(10);     // Redis
        // 1인 1개 제한인데 다시 살 수 있다 = 만료 주문은 구매 수량에서 빠짐
        assertThat(orderFacade.orderDirect(member, direct(ep)).status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void 기한_전에는_만료되지_않는다() {
        Long member = fixture.member();
        Long ep = fixture.ongoingEventProduct(10, 1);
        OrderDto.Response order = orderFacade.orderDirect(member, direct(ep));

        expireAll(order.paymentDeadline().minusSeconds(1));

        assertThat(statusOf(member, order)).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(fixture.remaining(ep)).isEqualTo(9);
    }

    @Test
    void 만료된_주문은_결제할_수_없다() {
        Long member = fixture.member();
        OrderDto.Response order = orderFacade.orderDirect(member, direct(fixture.ongoingEventProduct(10, 1)));
        expireAll(order.paymentDeadline().plusSeconds(1));

        assertErrorCode(() -> paymentService.pay(member, order.orderId(), CARD), ErrorCode.INVALID_ORDER_STATUS);
    }

    @Test
    void 결제한_주문은_기한이_지나도_만료되지_않는다() {
        Long member = fixture.member();
        Long ep = fixture.ongoingEventProduct(10, 1);
        OrderDto.Response order = orderFacade.orderDirect(member, direct(ep));
        paymentService.pay(member, order.orderId(), CARD);

        expireAll(order.paymentDeadline().plusMinutes(5));

        assertThat(statusOf(member, order)).isEqualTo(OrderStatus.PAID);
        assertThat(fixture.remaining(ep)).isEqualTo(9);
    }

    @Test
    void 결제한_주문을_취소하면_환불되고_재고가_돌아온다() {
        Long member = fixture.member();
        Long ep = fixture.ongoingEventProduct(10, 1);
        OrderDto.Response order = orderFacade.orderDirect(member, direct(ep));
        paymentService.pay(member, order.orderId(), CARD);

        OrderDto.Response canceled = orderFacade.cancel(member, order.orderId());

        assertThat(canceled.status()).isEqualTo(OrderStatus.CANCELED);
        assertThat(paymentRepository.findAllByOrderIdOrderByIdAsc(order.orderId()))
                .extracting(Payment::getStatus)
                .containsExactly(PaymentStatus.REFUNDED);
        assertThat(fixture.remaining(ep)).isEqualTo(10);
        assertThat(stockGate.remaining(ep)).isEqualTo(10);
    }

    @Test
    void 결제와_만료가_동시에_와도_한쪽만_반영된다() throws Exception {
        for (int round = 0; round < 10; round++) {
            Long member = fixture.member();
            Long ep = fixture.ongoingEventProduct(10, 1);
            OrderDto.Response order = orderFacade.orderDirect(member, direct(ep));
            LocalDateTime afterDeadline = order.paymentDeadline().plusSeconds(1);

            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            Future<Boolean> paid = executor.submit(awaitThen(start, () -> {
                try {
                    return paymentService.pay(member, order.orderId(), CARD).orderStatus() == OrderStatus.PAID;
                } catch (BusinessException e) {
                    return false; // 만료가 먼저 → INVALID_ORDER_STATUS
                }
            }));
            Future<Boolean> expired = executor.submit(awaitThen(start,
                    () -> orderService.expire(order.orderId(), afterDeadline).isPresent()));
            start.countDown();

            boolean payWon = paid.get();
            boolean expireWon = expired.get();
            executor.shutdown();

            assertThat(payWon ^ expireWon).as("정확히 한쪽만 성공 (round %d)", round).isTrue();
            OrderStatus finalStatus = statusOf(member, order);
            if (payWon) {
                assertThat(finalStatus).isEqualTo(OrderStatus.PAID);
                assertThat(fixture.remaining(ep)).isEqualTo(9);
            } else {
                assertThat(finalStatus).isEqualTo(OrderStatus.EXPIRED);
                assertThat(fixture.remaining(ep)).isEqualTo(10);
                assertThat(paymentRepository.findAllByOrderIdOrderByIdAsc(order.orderId())).isEmpty();
            }
        }
    }

    private static <T> Callable<T> awaitThen(CountDownLatch start, Callable<T> task) {
        return () -> {
            start.await();
            return task.call();
        };
    }

    /** 다른 테스트가 남긴 결제 대기 주문도 함께 만료될 수 있다 (각 테스트는 자기 데이터만 검증). */
    private void expireAll(LocalDateTime now) {
        orderFacade.expireOverdue(now, 100_000);
    }

    private OrderStatus statusOf(Long member, OrderDto.Response order) {
        return orderService.find(member, order.orderId()).status();
    }

    private static OrderDto.DirectRequest direct(Long eventProductId) {
        return new OrderDto.DirectRequest(eventProductId, 1);
    }

    private static void assertErrorCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }
}
