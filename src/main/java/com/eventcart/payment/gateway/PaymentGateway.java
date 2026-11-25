package com.eventcart.payment.gateway;

/**
 * PG사(토스페이먼츠, KG이니시스 등) 연동 지점. 지금은 {@link FakePaymentGateway}만 있다.
 * 실제 PG를 붙일 때는 이 인터페이스의 구현만 바꾸면 된다.
 */
public interface PaymentGateway {

    /** 결제 승인을 요청한다. 거절돼도 예외 대신 결과로 돌려준다. */
    ApprovalResult approve(String orderKey, int amount, String method);

    /** 승인된 결제를 환불한다. */
    void refund(String transactionId, int amount);

    record ApprovalResult(boolean approved, String transactionId, String failureReason) {

        public static ApprovalResult success(String transactionId) {
            return new ApprovalResult(true, transactionId, null);
        }

        public static ApprovalResult failure(String reason) {
            return new ApprovalResult(false, null, reason);
        }
    }
}
