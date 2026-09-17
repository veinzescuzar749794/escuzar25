package edu.cit.escuzar.shop.dto;

import edu.cit.escuzar.inventory.dto.InventoryItemView;

import java.util.List;

public record OrderResponse(
        Long orderId,
        String status,
        String reason,
        List<OrderItemOutcome> items,
        List<InventoryItemView> inventory
) {
}
