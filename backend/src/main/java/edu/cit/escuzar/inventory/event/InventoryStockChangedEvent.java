package edu.cit.escuzar.inventory.event;

/** Published after any committed stock change so external channels can sync the new quantity. */
public record InventoryStockChangedEvent(String productId, int available) {}
