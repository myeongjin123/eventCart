package com.eventcart.payment.domain;

public enum PaymentStatus {
    /** 결제 승인 */
    APPROVED,
    /** 결제 거절 (카드 한도 초과 등). 주문은 결제 대기 그대로 남아 다시 시도할 수 있다 */
    FAILED,
    /** 승인 후 주문 취소로 환불됨 */
    REFUNDED
}
