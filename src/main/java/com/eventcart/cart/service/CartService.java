package com.eventcart.cart.service;

import com.eventcart.cart.domain.CartItem;
import com.eventcart.cart.dto.CartDto;
import com.eventcart.cart.repository.CartItemRepository;
import com.eventcart.common.exception.BusinessException;
import com.eventcart.common.exception.ErrorCode;
import com.eventcart.event.domain.EventProduct;
import com.eventcart.event.service.EventService;
import com.eventcart.member.domain.Member;
import com.eventcart.member.service.MemberService;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final MemberService memberService;
    private final EventService eventService;
    private final Clock clock;

    public CartDto.Response getCart(Long memberId) {
        memberService.getMember(memberId);
        return CartDto.Response.from(cartItemRepository.findAllWithProductByMemberId(memberId));
    }

    /** 같은 이벤트 상품을 다시 담으면 수량을 합산한다. */
    @Transactional
    public CartDto.ItemResponse add(Long memberId, CartDto.AddRequest request) {
        Member member = memberService.getMember(memberId);
        EventProduct eventProduct = eventService.getEventProduct(request.eventProductId());
        eventProduct.validateOngoing(LocalDateTime.now(clock));

        CartItem item = cartItemRepository.findByMemberIdAndEventProductId(memberId, eventProduct.getId())
                .map(existing -> {
                    existing.addQuantity(request.quantity());
                    return existing;
                })
                .orElseGet(() -> {
                    CartItem created = new CartItem(member, eventProduct, 0);
                    created.changeQuantity(request.quantity());
                    return cartItemRepository.save(created);
                });
        return CartDto.ItemResponse.from(item);
    }

    @Transactional
    public CartDto.ItemResponse changeQuantity(Long memberId, Long cartItemId, CartDto.ChangeQuantityRequest request) {
        CartItem item = getOwnedItem(memberId, cartItemId);
        item.changeQuantity(request.quantity());
        return CartDto.ItemResponse.from(item);
    }

    @Transactional
    public void remove(Long memberId, Long cartItemId) {
        cartItemRepository.delete(getOwnedItem(memberId, cartItemId));
    }

    private CartItem getOwnedItem(Long memberId, Long cartItemId) {
        return cartItemRepository.findById(cartItemId)
                .filter(item -> item.isOwnedBy(memberId))
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));
    }
}
