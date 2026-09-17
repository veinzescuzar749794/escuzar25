package edu.cit.escuzar.inventory.event;

/**
 * Domain event published by Inventory module whenever a successful reservation
 * causes an item's remaining stock to drop below the configured threshold.
 */
public record LowStockEvent(
        String productId,
        String productName,
        int remainingStock,
        int threshold
) {
}

