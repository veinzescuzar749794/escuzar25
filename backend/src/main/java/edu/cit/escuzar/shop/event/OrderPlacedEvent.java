package edu.cit.escuzar.shop.event;

import java.util.List;

/**
 * Domain event published by Order module when an order passes all inventory
 * validations and is successfully confirmed.
 */
public record OrderPlacedEvent(
        Long orderId,
        List<OrderItemDto> items
) {
}

