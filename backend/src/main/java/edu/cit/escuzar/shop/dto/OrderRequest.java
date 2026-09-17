package edu.cit.escuzar.shop.dto;

import jakarta.validation.Valid;

import java.util.List;

/**
 * Order placement request.
 * Supports multi-item list { items: [{ productId, quantity }, ...] }
 * while maintaining backward compatibility with single-item requests.
 */
public record OrderRequest(
        @Valid List<OrderItemRequest> items,
        String productId,
        Integer quantity
) {
    public List<OrderItemRequest> effectiveItems() {
        if (items != null && !items.isEmpty()) {
            return items;
        }
        if (productId != null && !productId.isBlank() && quantity != null && quantity > 0) {
            return List.of(new OrderItemRequest(productId, quantity));
        }
        return List.of();
    }
}
