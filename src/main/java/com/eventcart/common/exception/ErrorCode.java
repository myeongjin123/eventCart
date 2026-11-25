package com.eventcart.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    MISSING_MEMBER_HEADER(HttpStatus.UNAUTHORIZED, "X-Member-Id 헤더가 필요합니다."),

    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),

    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),

    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "이벤트를 찾을 수 없습니다."),
    EVENT_PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "이벤트 상품을 찾을 수 없습니다."),
    INVALID_EVENT_PERIOD(HttpStatus.BAD_REQUEST, "이벤트 종료 시각은 시작 시각 이후여야 합니다."),
    EVENT_NOT_ONGOING(HttpStatus.BAD_REQUEST, "진행 중인 이벤트가 아닙니다."),

    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "장바구니 항목을 찾을 수 없습니다."),
    CART_EMPTY(HttpStatus.BAD_REQUEST, "장바구니가 비어 있습니다."),

    OUT_OF_STOCK(HttpStatus.CONFLICT, "재고가 부족합니다."),
    PURCHASE_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "1인 구매 제한 수량을 초과했습니다."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
    INVALID_ORDER_STATUS(HttpStatus.BAD_REQUEST, "현재 주문 상태에서는 처리할 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
