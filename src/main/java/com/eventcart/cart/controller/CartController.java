package com.eventcart.cart.controller;

import com.eventcart.cart.dto.CartDto;
import com.eventcart.cart.service.CartService;
import com.eventcart.common.web.MemberHeader;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    @GetMapping
    public CartDto.Response getCart(@RequestHeader(MemberHeader.NAME) Long memberId) {
        return cartService.getCart(memberId);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public CartDto.ItemResponse add(@RequestHeader(MemberHeader.NAME) Long memberId,
                                    @Valid @RequestBody CartDto.AddRequest request) {
        return cartService.add(memberId, request);
    }

    @PatchMapping("/items/{cartItemId}")
    public CartDto.ItemResponse changeQuantity(@RequestHeader(MemberHeader.NAME) Long memberId,
                                               @PathVariable Long cartItemId,
                                               @Valid @RequestBody CartDto.ChangeQuantityRequest request) {
        return cartService.changeQuantity(memberId, cartItemId, request);
    }

    @DeleteMapping("/items/{cartItemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@RequestHeader(MemberHeader.NAME) Long memberId, @PathVariable Long cartItemId) {
        cartService.remove(memberId, cartItemId);
    }
}
