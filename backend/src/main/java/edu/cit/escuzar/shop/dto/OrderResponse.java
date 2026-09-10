package edu.cit.escuzar.shop.dto;

import edu.cit.escuzar.inventory.dto.InventoryItemView;

public record OrderResponse(String status, String reason, InventoryItemView inventory) {
}
