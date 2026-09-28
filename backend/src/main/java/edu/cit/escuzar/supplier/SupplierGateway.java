package edu.cit.escuzar.supplier;

/**
 * Public Anti-Corruption Layer gateway interface.
 * Exposes methods strictly in internal domain terms (product ID and units needed).
 */
public interface SupplierGateway {

    /**
     * Places a purchase order for the requested product and units needed.
     * Converts internal units to supplier case quantity (rounding up), persists
     * the reorder record, and contacts the supplier with retry and idempotency safeguards.
     *
     * @param productId   internal product ID (e.g. "P100")
     * @param unitsNeeded internal number of units needed
     * @return the result of placing or queuing the supplier order
     */
    SupplierOrderResult placeOrder(String productId, int unitsNeeded);
}
