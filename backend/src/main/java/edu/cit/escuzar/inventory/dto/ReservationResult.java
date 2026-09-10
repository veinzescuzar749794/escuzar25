package edu.cit.escuzar.inventory.dto;

/**
 * Outcome of an inventory reservation attempt.
 *
 * @param success true if stock was decremented, false if rejected
 * @param reason  human-readable reason (null when success is true)
 * @param item    current state of the item after the attempt
 */
public record ReservationResult(boolean success, String reason, InventoryItemView item) {
}
