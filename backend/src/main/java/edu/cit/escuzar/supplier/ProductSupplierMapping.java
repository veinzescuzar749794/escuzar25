package edu.cit.escuzar.supplier;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Package-private anti-corruption translator that bridges internal product IDs
 * and LegacySupply's proprietary SKUs and wholesale pack sizes.
 */
@Component
class ProductSupplierMapping {

    record SkuMetadata(String supplierSku, int packSize) {}

    private final Map<String, SkuMetadata> internalToSupplier = new ConcurrentHashMap<>();
    private final Map<String, String> skuToInternal = new ConcurrentHashMap<>();

    ProductSupplierMapping() {
        // Default mapping established during contract discovery:
        // P100 (Wireless Mouse) -> WQC-6004 (PackSize 12)
        // P200 (Mechanical Keyboard) -> WQC-9692 (PackSize 24)
        // P300 (USB-C Hub) -> WQC-2303 (PackSize 12)
        registerMapping("P100", "WQC-6004", 12);
        registerMapping("P200", "WQC-9692", 24);
        registerMapping("P300", "WQC-2303", 12);
    }

    public void registerMapping(String productId, String supplierSku, int packSize) {
        internalToSupplier.put(productId, new SkuMetadata(supplierSku, packSize));
        skuToInternal.put(supplierSku, productId);
    }

    public boolean supportsProduct(String productId) {
        return internalToSupplier.containsKey(productId);
    }

    public String getSupplierSku(String productId) {
        SkuMetadata meta = internalToSupplier.get(productId);
        if (meta == null) {
            throw new IllegalArgumentException("Unknown supplier product mapping for productId: " + productId);
        }
        return meta.supplierSku();
    }

    public int getPackSize(String productId) {
        SkuMetadata meta = internalToSupplier.get(productId);
        if (meta == null) {
            throw new IllegalArgumentException("Unknown supplier product mapping for productId: " + productId);
        }
        return meta.packSize();
    }

    public String getProductIdBySku(String supplierSku) {
        return skuToInternal.get(supplierSku);
    }

    /**
     * Converts internal units needed to supplier case quantity, rounding up.
     * Whole number from 1 to 99.
     */
    public int calculateCases(String productId, int unitsNeeded) {
        if (unitsNeeded <= 0) {
            return 1;
        }
        int packSize = getPackSize(productId);
        int cases = (unitsNeeded + packSize - 1) / packSize;
        return Math.min(99, Math.max(1, cases));
    }

    /**
     * Calculates total retail units delivered for a given number of cases.
     */
    public int calculateDeliveredUnits(String productId, int cases) {
        return cases * getPackSize(productId);
    }
}
