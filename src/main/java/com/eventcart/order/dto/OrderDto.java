package com.eventcart.order.dto;

import com.eventcart.order.domain.Order;
import com.eventcart.order.domain.OrderItem;
import com.eventcart.order.domain.OrderStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

public final class OrderDto {

    private OrderDto() {
    }

    /** 바로 구매 */
    public record DirectRequest(@NotNull Long eventProductId, @Min(1) int quantity) {
    }

    public record Response(Long orderId, Long memberId, OrderStatus status, int totalPrice,
                           LocalDateTime orderedAt, LocalDateTime paymentDeadline, List<ItemResponse> items) {

        public static Response from(Order order) {
            return new Response(order.getId(), order.getMember().getId(), order.getStatus(), order.getTotalPrice(),
                    order.getOrderedAt(), order.getPaymentDeadline(),
                    order.getItems().stream().map(ItemResponse::from).toList());
        }
    }

    public record ItemResponse(Long eventProductId, String productName, int quantity, int unitPrice, int lineTotal) {

        public static ItemResponse from(OrderItem item) {
            return new ItemResponse(item.getEventProduct().getId(),
                    item.getEventProduct().getProduct().getName(),
                    item.getQuantity(), item.getUnitPrice(), item.getLineTotal());
        }
    }
}
