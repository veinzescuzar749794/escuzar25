package edu.cit.escuzar.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import edu.cit.escuzar.AppInstance;
import edu.cit.escuzar.inventory.InventoryService;
import edu.cit.escuzar.inventory.event.InventoryStockChangedEvent;
import edu.cit.escuzar.shop.OrderService;
import edu.cit.escuzar.shop.dto.OrderRequest;
import edu.cit.escuzar.shop.dto.OrderResponse;
import edu.cit.escuzar.shop.dto.OrderView;
import edu.cit.escuzar.supplier.SupplierGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChannelServiceTest {
    private final InventoryService inventory = mock(InventoryService.class);
    private final SupplierGateway suppliers = mock(SupplierGateway.class);
    private final ChannelCheckpointRepository checkpoints = mock(ChannelCheckpointRepository.class);
    private final ChannelOrderRepository channelOrders = mock(ChannelOrderRepository.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private FakeChannelHttpClient http;
    private StubOrderService orders;
    private ChannelService service;

    @BeforeEach
    void setUp() {
        ChannelProperties properties = new ChannelProperties("https://example.test", "client", "key");
        AppInstance instance = new AppInstance();
        http = new FakeChannelHttpClient(properties, instance, mapper);
        orders = new StubOrderService();
        service = new ChannelService(http, properties,
                instance, inventory, orders, suppliers, checkpoints, channelOrders, mapper);
        when(channelOrders.findAll()).thenReturn(List.of());
    }

    @Test
    void keepsEachStockChangeForTheSameSkuInOrder() {
        service.onStockChanged(new InventoryStockChangedEvent("P300", 12));
        service.onStockChanged(new InventoryStockChangedEvent("P300", 6));

        service.flushStockChanges();

        assertEquals(1, http.putBodies.size());
        assertEquals(List.of(
                Map.of("sellerSku", "P300", "available", 12),
                Map.of("sellerSku", "P300", "available", 6)), http.putBodies.get(0));
    }

    @Test
    void flushesStockImmediatelyAfterMarketplaceAcceptsAnOrder() throws Exception {
        when(channelOrders.existsById("TG-1")).thenReturn(false);
        when(channelOrders.existsByStatusStartingWith("PENDING_DECISION_")).thenReturn(true);
        orders.response = new OrderResponse(41L, "CONFIRMED", null, List.of(), List.of());
        service.onStockChanged(new InventoryStockChangedEvent("P300", 6));

        service.processNewOrder(mapper.readTree("""
                {"orderId":"TG-1","lines":[{"sellerSku":"P300","qty":1}]}
                """), "TG-1");

        assertEquals(List.of("/orders/TG-1/decision"), http.postPaths);
        assertEquals(1, http.putBodies.size());
    }

    @Test
    void confirmsDuplicateCancellationAsAlreadyRestockedWithoutRestockingTwice() throws Exception {
        ChannelOrder saved = new ChannelOrder("TG-2", 42L, "CANCELLED_BY_CUSTOMER", "[]");
        when(channelOrders.findById("TG-2")).thenReturn(Optional.of(saved));

        service.processCancellation("TG-2");

        assertEquals(List.of("/orders/TG-2/cancellation"), http.postPaths);
        assertEquals(Map.of("restocked", true), http.postBodies.get(0));
        assertEquals(0, orders.cancelCalls);
    }

    private static class FakeChannelHttpClient extends ChannelHttpClient {
        private final ObjectMapper testMapper;
        private final List<Object> putBodies = new java.util.ArrayList<>();
        private final List<String> postPaths = new java.util.ArrayList<>();
        private final List<Object> postBodies = new java.util.ArrayList<>();

        FakeChannelHttpClient(ChannelProperties properties, AppInstance instance, ObjectMapper mapper) {
            super(properties, instance, mapper);
            this.testMapper = mapper;
        }

        @Override JsonNode get(String path) { return testMapper.createObjectNode(); }
        @Override JsonNode post(String path, Object body) {
            postPaths.add(path);
            postBodies.add(body);
            return testMapper.createObjectNode();
        }
        @Override JsonNode put(String path, Object body) {
            putBodies.add(body);
            return testMapper.createObjectNode();
        }
    }

    private static class StubOrderService extends OrderService {
        private OrderResponse response;
        private int cancelCalls;

        StubOrderService() { super(null, null, null); }

        @Override public OrderResponse placeOrder(OrderRequest request, String sourceReference) {
            return response;
        }

        @Override public OrderView cancelOrder(Long orderId) {
            cancelCalls++;
            return null;
        }
    }
}
