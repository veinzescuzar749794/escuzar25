package edu.cit.escuzar.inventory;

import edu.cit.escuzar.inventory.dto.InventoryItemView;
import edu.cit.escuzar.inventory.dto.ReservationResult;

import java.util.Optional;

/**
 * The public contract of the Inventory module. This is the module
 * boundary: the Order module may depend on this interface and on the
 * dto package, and on nothing else in edu.cit.escuzar.inventory.
 */
public interface InventoryService {

    Optional<InventoryItemView> getItem(String productId);

    /**
     * Attempts to reserve {@code quantity} units of {@code productId}.
     * Rejects (success = false) if the product doesn't exist or the
     * requested quantity exceeds current stock. Never throws for
     * ordinary business rejections -- callers check success/reason.
     */
    ReservationResult reserve(String productId, int quantity);
}
