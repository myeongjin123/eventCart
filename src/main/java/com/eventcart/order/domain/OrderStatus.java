package com.eventcart.order.domain;

public enum OrderStatus {
    /** 주문 완료 (재고 차감됨). 결제 단계는 로드맵 6단계에서 추가 */
    ORDERED,
    /** 취소 (재고 복구됨) */
    CANCELED
}
