package edu.cit.escuzar.shop.event;

import java.util.List;

/**
 * Domain event published by Order module when an order fails inventory
 * validation (e.g. insufficient stock or missing item) and is rejected with
 * no stock reserved.
 */
public record OrderRejectedEvent(
        Long orderId,
        String reason,
        List<OrderItemDto> items
) {
}

