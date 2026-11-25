package com.eventcart.event.domain;

/** 이벤트 상품이 등록됐다 (커밋 후 Redis 재고 게이트를 미리 채우는 데 쓴다). */
public record EventProductRegistered(Long eventProductId) {
}
