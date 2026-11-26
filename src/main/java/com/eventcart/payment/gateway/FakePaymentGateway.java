package com.eventcart.payment.gateway;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 모의 PG. 결제 수단이 {@value #DECLINED_METHOD}면 거절하고, 나머지는 모두 승인한다.
 * (실패 흐름을 화면·테스트에서 재현하기 위한 규칙)
 */
@Slf4j
@Component
public class FakePaymentGateway implements PaymentGateway {

    public static final String DECLINED_METHOD = "DECLINED_CARD";

    @Override
    public ApprovalResult approve(String orderKey, int amount, String method) {
        if (DECLINED_METHOD.equals(method)) {
            return ApprovalResult.failure("카드사 승인 거절 (한도 초과)");
        }
        String transactionId = "fake-" + UUID.randomUUID();
        log.info("[FakePG] 승인 orderKey={} amount={} method={} tx={}", orderKey, amount, method, transactionId);
        return ApprovalResult.success(transactionId);
    }

    @Override
    public void refund(String transactionId, int amount) {
        log.info("[FakePG] 환불 tx={} amount={}", transactionId, amount);
    }
}
