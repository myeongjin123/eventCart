package com.eventcart.order.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * <pre>
 *  PENDING_PAYMENT ──결제──▶ PAID ──취소(환불)──▶ CANCELED
 *        │  └──────────취소────────────────────▶ CANCELED
 *        └──결제 기한 지남(스케줄러)──▶ EXPIRED
 * </pre>
 * 재고는 주문 시점(PENDING_PAYMENT)에 차감하고, CANCELED·EXPIRED가 되면 돌려준다.
 */
public enum OrderStatus {
    /** 주문 접수, 재고 차감됨, 결제 기한 안에 결제해야 한다 */
    PENDING_PAYMENT,
    /** 결제 완료 */
    PAID,
    /** 사용자가 취소 (결제됐으면 환불) */
    CANCELED,
    /** 결제 기한이 지나 자동 취소 */
    EXPIRED;

    /** 재고를 쥐고 있고 1인 구매 제한에 포함되는 상태 */
    public static final Set<OrderStatus> ACTIVE = EnumSet.of(PENDING_PAYMENT, PAID);
}
