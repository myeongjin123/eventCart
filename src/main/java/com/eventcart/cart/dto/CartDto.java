package com.eventcart.cart.dto;

import com.eventcart.cart.domain.CartItem;
import com.eventcart.event.domain.EventProduct;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public final class CartDto {

    private CartDto() {
    }

    public record AddRequest(@NotNull Long eventProductId, @Min(1) int quantity) {
    }

    public record ChangeQuantityRequest(@Min(1) int quantity) {
    }

    public record ItemResponse(Long cartItemId, Long eventProductId, String productName,
                               int eventPrice, int quantity, int lineTotal, int remainingQuantity) {

        public static ItemResponse from(CartItem item) {
            EventProduct ep = item.getEventProduct();
            return new ItemResponse(item.getId(), ep.getId(), ep.getProduct().getName(),
                    ep.getEventPrice(), item.getQuantity(), ep.getEventPrice() * item.getQuantity(),
                    ep.getRemainingQuantity());
        }
    }

    public record Response(List<ItemResponse> items, int totalPrice) {

        public static Response from(List<CartItem> items) {
            List<ItemResponse> responses = items.stream().map(ItemResponse::from).toList();
            int total = responses.stream().mapToInt(ItemResponse::lineTotal).sum();
            return new Response(responses, total);
        }
    }
}
