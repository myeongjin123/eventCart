package com.eventcart.support;

import com.eventcart.event.dto.EventDto;
import com.eventcart.event.service.EventService;
import com.eventcart.member.dto.MemberDto;
import com.eventcart.member.service.MemberService;
import com.eventcart.product.dto.ProductDto;
import com.eventcart.product.service.ProductService;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;

/** 테스트 데이터 생성 도우미. 각 호출이 고유한 데이터를 만들어 테스트 간 정리가 필요 없다. */
@TestComponent
@RequiredArgsConstructor
public class TestFixture {

    private final MemberService memberService;
    private final ProductService productService;
    private final EventService eventService;

    public Long member() {
        String email = UUID.randomUUID() + "@test.com";
        return memberService.create(new MemberDto.CreateRequest(email, "tester", "password123")).id();
    }

    /** 진행 중인 이벤트에 상품을 등록하고 eventProductId를 반환한다. */
    public Long ongoingEventProduct(int stock, int perMemberLimit) {
        LocalDateTime now = LocalDateTime.now();
        Long eventId = eventService.create(
                new EventDto.CreateRequest("테스트 이벤트", now.minusHours(1), now.plusHours(1))).id();
        Long productId = productService.create(new ProductDto.CreateRequest("한정 상품", 10_000)).id();
        return eventService.addProduct(eventId,
                new EventDto.AddProductRequest(productId, 5_000, stock, perMemberLimit)).eventProductId();
    }

    /** 이미 끝난 이벤트의 상품. Redis 게이트는 통과하지만 DB 검증(기간)에서 실패시키는 용도. */
    public Long endedEventProduct(int stock, int perMemberLimit) {
        LocalDateTime now = LocalDateTime.now();
        Long eventId = eventService.create(
                new EventDto.CreateRequest("끝난 이벤트", now.minusHours(2), now.minusHours(1))).id();
        Long productId = productService.create(new ProductDto.CreateRequest("지난 상품", 10_000)).id();
        return eventService.addProduct(eventId,
                new EventDto.AddProductRequest(productId, 5_000, stock, perMemberLimit)).eventProductId();
    }

    public int remaining(Long eventProductId) {
        return eventService.getEventProduct(eventProductId).getRemainingQuantity();
    }
}
