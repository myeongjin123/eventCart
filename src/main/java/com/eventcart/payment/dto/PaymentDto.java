package com.eventcart.payment.dto;

import com.eventcart.order.domain.OrderStatus;
import com.eventcart.payment.domain.Payment;
import com.eventcart.payment.domain.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class PaymentDto {

    private PaymentDto() {
    }

    /** @param method 결제 수단 (예: CARD). 모의 PG는 DECLINED_CARD면 거절한다. */
    public record PayRequest(@NotBlank @Size(max = 30) String method) {
    }

    public record Response(Long paymentId, Long orderId, PaymentStatus paymentStatus, OrderStatus orderStatus,
                           int amount, String failureReason) {

        public static Response of(Payment payment, OrderStatus orderStatus) {
            return new Response(payment.getId(), payment.getOrder().getId(), payment.getStatus(), orderStatus,
                    payment.getAmount(), payment.getFailureReason());
        }
    }
}
