package edu.cit.escuzar.shop.event;

public record OrderItemDto(
        String productId,
        int quantity
) {
}

