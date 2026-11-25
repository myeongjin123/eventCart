package com.eventcart.event.dto;

import com.eventcart.event.domain.Event;
import com.eventcart.event.domain.EventProduct;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class EventDto {

    private EventDto() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull LocalDateTime startAt,
            @NotNull LocalDateTime endAt) {
    }

    public record AddProductRequest(
            @NotNull Long productId,
            @PositiveOrZero int eventPrice,
            @Min(1) int totalQuantity,
            @Min(1) int perMemberLimit) {
    }

    public record Response(Long id, String name, LocalDateTime startAt, LocalDateTime endAt,
                           List<EventProductResponse> products) {

        public static Response from(Event event) {
            return new Response(event.getId(), event.getName(), event.getStartAt(), event.getEndAt(),
                    event.getEventProducts().stream().map(EventProductResponse::from).toList());
        }
    }

    public record EventProductResponse(Long eventProductId, Long productId, String productName,
                                       int originalPrice, int eventPrice,
                                       int totalQuantity, int remainingQuantity, int perMemberLimit) {

        public static EventProductResponse from(EventProduct ep) {
            return new EventProductResponse(ep.getId(), ep.getProduct().getId(), ep.getProduct().getName(),
                    ep.getProduct().getPrice(), ep.getEventPrice(),
                    ep.getTotalQuantity(), ep.getRemainingQuantity(), ep.getPerMemberLimit());
        }
    }
}
