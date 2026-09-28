package edu.cit.escuzar.supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupplierGatewayImplTest {

    private final ProductSupplierMapping productMapping = new ProductSupplierMapping();
    private final LegacySupplyClient httpClient = mock(LegacySupplyClient.class);
    private final SupplierOrderRepository repository = mock(SupplierOrderRepository.class);
    private SupplierGatewayImpl gateway;

    @BeforeEach
    void setUp() {
        gateway = new SupplierGatewayImpl(productMapping, httpClient, repository);
    }

    @Test
    void placesOrderSuccessfullyWithAck() {
        when(repository.saveAndFlush(any(SupplierOrder.class))).thenAnswer(inv -> {
            SupplierOrder o = inv.getArgument(0);
            if (o.getId() == null) {
                o.setId(42L);
            }
            return o;
        });
        when(repository.save(any(SupplierOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        LegacySupplyXmlParser.PurchaseOrderAckDto ack = new LegacySupplyXmlParser.PurchaseOrderAckDto(
                "PO-100231", 10, "WQC-6004", 2, "CS", "RO-42", "2026-09-28T10:00:00Z"
        );
        when(httpClient.placePurchaseOrder(eq("WQC-6004"), eq(2), eq("RO-42"), any()))
                .thenReturn(ack);

        // 17 units of P100 (PackSize 12) -> 2 cases, 24 units
        SupplierOrderResult result = gateway.placeOrder("P100", 17);

        assertEquals(42L, result.orderId());
        assertEquals("P100", result.productId());
        assertEquals("RO-42", result.buyerRef());
        assertEquals(2, result.cases());
        assertEquals(24, result.units());
        assertEquals(SupplierOrderStatus.ACCEPTED, result.status());
        assertEquals("PO-100231", result.poNumber());

        ArgumentCaptor<SupplierOrder> captor = ArgumentCaptor.forClass(SupplierOrder.class);
        verify(repository).save(captor.capture());
        assertEquals(SupplierOrderStatus.ACCEPTED, captor.getValue().getStatus());
        assertEquals("PO-100231", captor.getValue().getPoNumber());
    }

    @Test
    void keepsOrderAsPendingWhenSupplierFailsDueToOutage() {
        when(repository.saveAndFlush(any(SupplierOrder.class))).thenAnswer(inv -> {
            SupplierOrder o = inv.getArgument(0);
            if (o.getId() == null) {
                o.setId(99L);
            }
            return o;
        });

        when(httpClient.placePurchaseOrder(any(), anyInt(), any(), any()))
                .thenThrow(new IllegalStateException("503 Service Unavailable"));

        SupplierOrderResult result = gateway.placeOrder("P100", 5);

        assertEquals(99L, result.orderId());
        assertEquals(SupplierOrderStatus.PENDING, result.status());
        assertTrue(result.message().contains("PENDING due to supplier unavailability"));
    }

    @Test
    void mapsLegacySupplyStatusCodesProperly() {
        assertEquals(SupplierOrderStatus.ACCEPTED, SupplierGatewayImpl.mapStatusCode(10));
        assertEquals(SupplierOrderStatus.PICKING, SupplierGatewayImpl.mapStatusCode(20));
        assertEquals(SupplierOrderStatus.SHIPPED, SupplierGatewayImpl.mapStatusCode(30));
        assertEquals(SupplierOrderStatus.DELIVERED, SupplierGatewayImpl.mapStatusCode(40));
        assertEquals(SupplierOrderStatus.CANCELLED, SupplierGatewayImpl.mapStatusCode(50));
        assertEquals(SupplierOrderStatus.CANCELLED, SupplierGatewayImpl.mapStatusCode(90));
        assertEquals(SupplierOrderStatus.UNKNOWN, SupplierGatewayImpl.mapStatusCode(999));
    }

    private int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }
}
