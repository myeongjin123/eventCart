package com.eventcart.payment.service;

import com.eventcart.common.exception.BusinessException;
import com.eventcart.common.exception.ErrorCode;
import com.eventcart.order.domain.Order;
import com.eventcart.order.repository.OrderRepository;
import com.eventcart.payment.domain.Payment;
import com.eventcart.payment.dto.PaymentDto;
import com.eventcart.payment.gateway.PaymentGateway;
import com.eventcart.payment.gateway.PaymentGateway.ApprovalResult;
import com.eventcart.payment.repository.PaymentRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final Clock clock;

    /**
     * 주문 행을 잠그고 결제한다. 같은 주문을 동시에 두 번 결제하거나, 결제와 만료 스케줄러가
     * 동시에 처리하려 해도 먼저 락을 잡은 쪽만 성공하고 나머지는 바뀐 상태를 보고 거절된다.
     *
     * <p>거절(카드 한도 초과 등)은 예외가 아니다. 실패 기록을 남기고 주문은 결제 대기 그대로 둔다.
     *
     * <p>한계: 모의 PG라 락을 쥔 채 PG를 호출한다. 실제 PG는 수 초가 걸릴 수 있으므로
     * 'PAYING' 상태로 먼저 커밋하고 트랜잭션 밖에서 호출하는 방식으로 바꿔야 한다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentDto.Response pay(Long memberId, Long orderId, PaymentDto.PayRequest request) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .filter(o -> o.isOwnedBy(memberId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock);
        order.validatePayable(now);

        ApprovalResult result = paymentGateway.approve("order-" + order.getId(), order.getTotalPrice(), request.method());
        Payment payment;
        if (result.approved()) {
            order.markPaid(now);
            payment = Payment.approved(order, request.method(), result.transactionId(), now);
        } else {
            payment = Payment.failed(order, request.method(), result.failureReason(), now);
        }
        paymentRepository.save(payment);
        return PaymentDto.Response.of(payment, order.getStatus());
    }
}
