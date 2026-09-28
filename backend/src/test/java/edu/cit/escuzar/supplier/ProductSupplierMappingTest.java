package edu.cit.escuzar.supplier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductSupplierMappingTest {

    private final ProductSupplierMapping mapping = new ProductSupplierMapping();

    @Test
    void verifiesCatalogMappings() {
        assertTrue(mapping.supportsProduct("P100"));
        assertTrue(mapping.supportsProduct("P200"));
        assertTrue(mapping.supportsProduct("P300"));

        assertEquals("WQC-6004", mapping.getSupplierSku("P100"));
        assertEquals(12, mapping.getPackSize("P100"));

        assertEquals("WQC-9692", mapping.getSupplierSku("P200"));
        assertEquals(24, mapping.getPackSize("P200"));

        assertEquals("WQC-2303", mapping.getSupplierSku("P300"));
        assertEquals(12, mapping.getPackSize("P300"));
    }

    @Test
    void calculatesCasesRoundingUp() {
        // P100 PackSize is 12
        assertEquals(1, mapping.calculateCases("P100", 1));
        assertEquals(1, mapping.calculateCases("P100", 12));
        assertEquals(2, mapping.calculateCases("P100", 13));
        assertEquals(2, mapping.calculateCases("P100", 24));
        assertEquals(3, mapping.calculateCases("P100", 25));

        // P200 PackSize is 24
        assertEquals(1, mapping.calculateCases("P200", 10));
        assertEquals(1, mapping.calculateCases("P200", 24));
        assertEquals(2, mapping.calculateCases("P200", 25));
    }

    @Test
    void calculatesDeliveredUnits() {
        assertEquals(12, mapping.calculateDeliveredUnits("P100", 1));
        assertEquals(24, mapping.calculateDeliveredUnits("P100", 2));
        assertEquals(48, mapping.calculateDeliveredUnits("P200", 2));
    }

    @Test
    void throwsOnUnknownProduct() {
        assertThrows(IllegalArgumentException.class, () -> mapping.getSupplierSku("UNKNOWN"));
    }
}
