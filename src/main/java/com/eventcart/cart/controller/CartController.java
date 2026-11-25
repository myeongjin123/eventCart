package com.eventcart.cart.controller;

import com.eventcart.cart.dto.CartDto;
import com.eventcart.cart.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    @GetMapping
    public CartDto.Response getCart(@AuthenticationPrincipal Long memberId) {
        return cartService.getCart(memberId);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public CartDto.ItemResponse add(@AuthenticationPrincipal Long memberId,
                                    @Valid @RequestBody CartDto.AddRequest request) {
        return cartService.add(memberId, request);
    }

    @PatchMapping("/items/{cartItemId}")
    public CartDto.ItemResponse changeQuantity(@AuthenticationPrincipal Long memberId,
                                               @PathVariable Long cartItemId,
                                               @Valid @RequestBody CartDto.ChangeQuantityRequest request) {
        return cartService.changeQuantity(memberId, cartItemId, request);
    }

    @DeleteMapping("/items/{cartItemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal Long memberId, @PathVariable Long cartItemId) {
        cartService.remove(memberId, cartItemId);
    }
}
