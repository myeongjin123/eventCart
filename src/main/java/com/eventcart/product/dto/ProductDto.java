package com.eventcart.product.dto;

import com.eventcart.product.domain.Product;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class ProductDto {

    private ProductDto() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 100) String name,
            @PositiveOrZero int price) {
    }

    public record Response(Long id, String name, int price) {

        public static Response from(Product product) {
            return new Response(product.getId(), product.getName(), product.getPrice());
        }
    }
}
