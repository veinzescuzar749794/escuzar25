package edu.cit.escuzar.inventory.dto;

/**
 * Immutable snapshot of an inventory item. This is what crosses the
 * module boundary into the Order module -- never the JPA entity itself,
 * so the Order module can never accidentally persist changes to
 * inventory rows.
 */
public record InventoryItemView(String productId, String name, int stock) {
}
