package edu.cit.escuzar.shop.dto;

import java.time.Instant;
import java.util.List;

public record OrderView(
        Long orderId,
        String status,
        String reason,
        Instant createdAt,
        List<OrderItemView> items
) {
    public record OrderItemView(
            String productId,
            int quantity
    ) {
    }
}

