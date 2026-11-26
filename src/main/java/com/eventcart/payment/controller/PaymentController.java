package com.eventcart.payment.controller;

import com.eventcart.payment.dto.PaymentDto;
import com.eventcart.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /** 결제 요청. 승인·거절 모두 200으로 응답하고 paymentStatus로 결과를 알려준다. */
    @PostMapping("/api/orders/{orderId}/payment")
    public PaymentDto.Response pay(@AuthenticationPrincipal Long memberId, @PathVariable Long orderId,
                                   @Valid @RequestBody PaymentDto.PayRequest request) {
        return paymentService.pay(memberId, orderId, request);
    }
}
