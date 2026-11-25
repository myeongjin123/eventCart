package com.eventcart.order.controller;

import com.eventcart.common.web.MemberHeader;
import com.eventcart.order.dto.OrderDto;
import com.eventcart.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDto.Response orderDirect(@RequestHeader(MemberHeader.NAME) Long memberId,
                                         @Valid @RequestBody OrderDto.DirectRequest request) {
        return orderService.orderDirect(memberId, request);
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDto.Response checkout(@RequestHeader(MemberHeader.NAME) Long memberId) {
        return orderService.checkout(memberId);
    }

    @GetMapping
    public List<OrderDto.Response> findMyOrders(@RequestHeader(MemberHeader.NAME) Long memberId) {
        return orderService.findMyOrders(memberId);
    }

    @GetMapping("/{orderId}")
    public OrderDto.Response find(@RequestHeader(MemberHeader.NAME) Long memberId, @PathVariable Long orderId) {
        return orderService.find(memberId, orderId);
    }

    @PostMapping("/{orderId}/cancel")
    public OrderDto.Response cancel(@RequestHeader(MemberHeader.NAME) Long memberId, @PathVariable Long orderId) {
        return orderService.cancel(memberId, orderId);
    }
}
