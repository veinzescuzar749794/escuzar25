package edu.cit.escuzar.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cit.escuzar.AppInstance;
import edu.cit.escuzar.inventory.InventoryService;
import edu.cit.escuzar.inventory.dto.InventoryItemView;
import edu.cit.escuzar.inventory.event.InventoryStockChangedEvent;
import edu.cit.escuzar.shop.OrderService;
import edu.cit.escuzar.shop.dto.OrderItemRequest;
import edu.cit.escuzar.shop.dto.OrderRequest;
import edu.cit.escuzar.shop.dto.OrderResponse;
import edu.cit.escuzar.shop.dto.OrderView;
import edu.cit.escuzar.shop.event.OrderCancelledEvent;
import edu.cit.escuzar.supplier.SupplierGateway;
import edu.cit.escuzar.supplier.SupplierOrderDeliveredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

/** Tiangge anti-corruption layer. All marketplace protocol details stay package-private. */
@Service
class ChannelService implements ChannelGateway, ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ChannelService.class);
    private final ChannelHttpClient http;
    private final ChannelProperties properties;
    private final AppInstance instance;
    private final InventoryService inventory;
    private final OrderService orders;
    private final SupplierGateway suppliers;
    private final ChannelCheckpointRepository checkpoints;
    private final ChannelOrderRepository channelOrders;
    private final ObjectMapper mapper;
    private final ConcurrentLinkedDeque<InventoryStockChangedEvent> pendingStock = new ConcurrentLinkedDeque<>();

    ChannelService(ChannelHttpClient http, ChannelProperties properties, AppInstance instance,
                   InventoryService inventory, OrderService orders, SupplierGateway suppliers,
                   ChannelCheckpointRepository checkpoints,
                   ChannelOrderRepository channelOrders, ObjectMapper mapper) {
        this.http = http; this.properties = properties; this.instance = instance; this.inventory = inventory;
        this.orders = orders; this.suppliers = suppliers;
        this.checkpoints = checkpoints; this.channelOrders = channelOrders; this.mapper = mapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.apiKey.isBlank() || properties.clientId.isBlank()) {
            log.warn("Tiangge adapter disabled: configure TIANGGE_CLIENT_ID and TIANGGE_API_KEY.");
            return;
        }
        heartbeat();
        publishListingsAndStock();
        pollFeed();
    }

    @Scheduled(fixedDelayString = "${tiangge.heartbeat-ms:30000}", initialDelay = 30000)
    void heartbeat() {
        if (!enabled()) return;
        try {
            JsonNode response = http.post("/instances/heartbeat", Map.of(
                    "appName", "escuzar-shop", "startedAt", instance.startedAt().toString(),
                    "uptimeSeconds", instance.uptimeSeconds()));
            long next = response.path("nextHeartbeatSeconds").asLong(30);
            log.info("Tiangge heartbeat accepted for instance {} (next in {}s)", instance.id(), next);
        } catch (Exception e) { log.warn("Tiangge heartbeat failed: {}", e.getMessage()); }
    }

    @Override
    public void publishListingsAndStock() {
        if (!enabled()) return;
        try {
            List<Map<String, Object>> listings = new ArrayList<>();
            for (InventoryItemView item : inventory.getAllItems()) listings.add(Map.of(
                    "sellerSku", item.productId(), "title", item.name(),
                    "supplierSku", suppliers.getSupplierSku(item.productId())));
            http.put("/listings", listings);
            publishStock(inventory.getAllItems());
        } catch (Exception e) { log.warn("Publishing Tiangge listings/stock failed: {}", e.getMessage()); }
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    void retryListings() { publishListingsAndStock(); }

    @EventListener
    void onStockChanged(InventoryStockChangedEvent event) {
        pendingStock.addLast(event);
    }

    @Scheduled(fixedDelay = 500)
    void flushStockChanges() {
        flushStockChanges(false);
    }

    private void flushStockChanges(boolean afterMarketplaceAcceptance) {
        if (!enabled() || pendingStock.isEmpty()) return;
        if (!afterMarketplaceAcceptance && (channelOrders.existsByStatusStartingWith("PENDING_DECISION_")
                || channelOrders.existsByStatus("PROCESSING"))) return;
        List<InventoryStockChangedEvent> batch = new ArrayList<>();
        InventoryStockChangedEvent event;
        while ((event = pendingStock.pollFirst()) != null) batch.add(event);
        if (batch.isEmpty()) return;
        try {
            http.put("/stock", batch.stream().map(e -> Map.of(
                    "sellerSku", e.productId(), "available", e.available())).toList());
        } catch (Exception e) {
            for (int i = batch.size() - 1; i >= 0; i--) pendingStock.addFirst(batch.get(i));
            log.warn("Tiangge event-driven stock update failed; will retry: {}", e.getMessage());
            return;
        }
        batch.stream().map(InventoryStockChangedEvent::productId).distinct().forEach(this::resolveReadyBackorders);
    }

    @EventListener
    void onOrderCancelled(OrderCancelledEvent event) {
        // Stock events are emitted by Inventory for each restocked line.
    }

    @EventListener
    void onSupplierDelivered(SupplierOrderDeliveredEvent event) {
        // Inventory's delivery listener updates stock; the ensuing stock event triggers backorder processing.
    }

    @Scheduled(fixedDelayString = "${tiangge.feed-poll-ms:3000}", initialDelay = 3000)
    void pollFeed() {
        if (!enabled()) return;
        try {
            ChannelCheckpoint cp = checkpoints.findById(1).orElseGet(() -> checkpoints.save(new ChannelCheckpoint(1, 0)));
            JsonNode feed = http.get("/feed?after=" + cp.getCursor() + "&limit=50");
            JsonNode events = feed.path("events");
            long cursor = cp.getCursor();
            for (JsonNode event : events) {
                processFeedEvent(event);
                cursor = Math.max(cursor, event.path("seq").asLong());
                cp.setCursor(cursor);
                checkpoints.save(cp);
            }
            long nextCursor = feed.path("nextCursor").asLong(cursor);
            if (nextCursor > cursor) { cp.setCursor(nextCursor); checkpoints.save(cp); }
        } catch (Exception e) { log.warn("Tiangge feed poll failed; cursor will be retried: {}", e.getMessage()); }
    }

    private void processFeedEvent(JsonNode event) throws Exception {
        String type = event.path("type").asText();
        String marketplaceOrderId = event.path("orderId").asText();
        if ("ORDER_PLACED".equals(type)) processNewOrder(event, marketplaceOrderId);
        else if ("ORDER_CANCELLED".equals(type)) processCancellation(marketplaceOrderId);
        else log.info("Skipping unsupported Tiangge feed event type {}", type);
    }

    void processNewOrder(JsonNode event, String marketplaceOrderId) throws Exception {
        if (channelOrders.existsById(marketplaceOrderId)) {
            ChannelOrder saved = channelOrders.findById(marketplaceOrderId).orElseThrow();
            if (saved.getStatus().startsWith("PENDING_DECISION_")) {
                String decision = saved.getStatus().substring("PENDING_DECISION_".length());
                sendDecision(saved, decision, saved.getShopOrderId(), null);
            } else if ("PROCESSING".equals(saved.getStatus())) {
                List<OrderItemRequest> items = mapper.readerForListOf(OrderItemRequest.class).readValue(saved.getLinesJson());
                completeNewOrder(saved, items);
            }
            return;
        }
        List<OrderItemRequest> items = new ArrayList<>();
        JsonNode lines = event.path("lines");
        for (JsonNode line : lines) items.add(new OrderItemRequest(line.path("sellerSku").asText(), line.path("qty").asInt()));
        ChannelOrder saved = new ChannelOrder(marketplaceOrderId, null, "PROCESSING", mapper.writeValueAsString(items));
        channelOrders.saveAndFlush(saved);
        completeNewOrder(saved, items);
    }

    private void completeNewOrder(ChannelOrder saved, List<OrderItemRequest> items) throws Exception {
        OrderResponse response = orders.placeOrder(new OrderRequest(items, null, null),
                "tiangge:" + saved.getMarketplaceOrderId());
        String status = "CONFIRMED".equals(response.status()) ? "ACCEPTED" : "REJECTED";
        if ("REJECTED".equals(status) && canBackorder(items)) status = "BACKORDERED";
        saved.setShopOrderId(response.orderId());
        saved.setStatus("PENDING_DECISION_" + status);
        channelOrders.saveAndFlush(saved);
        String reason = response.reason();
        sendDecision(saved, status, response.orderId(), reason);
    }

    private void sendDecision(ChannelOrder order, String decision, Long shopOrderId, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>(); payload.put("decision", decision);
        if (shopOrderId != null) payload.put("shopOrderId", shopOrderId.toString());
        if (reason != null && !reason.isBlank()) payload.put("reason", reason.substring(0, Math.min(200, reason.length())));
        http.post("/orders/" + order.getMarketplaceOrderId() + "/decision", payload);
        order.setStatus(decision); channelOrders.save(order);
        flushStockChanges("ACCEPTED".equals(decision));
    }

    private boolean canBackorder(List<OrderItemRequest> items) {
        Map<String, Integer> requested = new LinkedHashMap<>();
        for (OrderItemRequest line : items) {
            if (line.quantity() <= 0) return false;
            requested.merge(line.productId(), line.quantity(), Integer::sum);
        }
        Map<String, Integer> shortages = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : requested.entrySet()) {
            var item = inventory.getItem(entry.getKey());
            if (item.isEmpty()) return false;
            int missing = entry.getValue() - item.get().stock();
            if (missing > 0) shortages.put(entry.getKey(), missing);
        }
        if (shortages.isEmpty()) return false;

        for (Map.Entry<String, Integer> shortage : shortages.entrySet()) {
            if (!suppliers.hasOpenOrderForProduct(shortage.getKey())) {
                try { suppliers.placeOrder(shortage.getKey(), shortage.getValue()); }
                catch (Exception e) { log.warn("Could not queue supplier reorder for {}: {}", shortage.getKey(), e.getMessage()); }
            }
            if (!suppliers.hasOpenOrderForProduct(shortage.getKey())) return false;
        }
        return true;
    }

    private void resolveReadyBackorders(String productId) {
        if (!enabled()) return;
        for (ChannelOrder channelOrder : channelOrders.findAll()) {
            if (!"BACKORDERED".equals(channelOrder.getStatus())) continue;
            try {
                List<OrderItemRequest> items = mapper.readerForListOf(OrderItemRequest.class).readValue(channelOrder.getLinesJson());
                if (items.stream().noneMatch(i -> i.productId().equals(productId))) continue;
                Map<String, Integer> requested = new LinkedHashMap<>();
                for (OrderItemRequest item : items) requested.merge(item.productId(), item.quantity(), Integer::sum);
                Map<String, Integer> stockSnapshot = new LinkedHashMap<>();
                boolean ready = true;
                for (Map.Entry<String, Integer> entry : requested.entrySet()) {
                    var inventoryItem = inventory.getItem(entry.getKey());
                    if (inventoryItem.isEmpty() || entry.getValue() > inventoryItem.get().stock()) {
                        ready = false;
                        break;
                    }
                    stockSnapshot.put(entry.getKey(), inventoryItem.get().stock());
                }
                if (!ready) continue;
                OrderResponse retry = orders.placeOrder(new OrderRequest(items, null, null),
                        "tiangge:" + channelOrder.getMarketplaceOrderId() + ":fulfillment:" + stockSnapshotHash(stockSnapshot));
                if ("CONFIRMED".equals(retry.status())) {
                    channelOrder.setShopOrderId(retry.orderId());
                    resolveBackorder(channelOrder, "ACCEPTED");
                } else if (items.stream().filter(i -> i.quantity() > inventory.getItem(i.productId()).map(v -> v.stock()).orElse(0))
                        .noneMatch(i -> suppliers.hasOpenOrderForProduct(i.productId()))) {
                    resolveBackorder(channelOrder, "CANCELLED");
                }
            } catch (Exception e) { log.warn("Backorder {} resolution will retry: {}", channelOrder.getMarketplaceOrderId(), e.getMessage()); }
        }
    }

    private String stockSnapshotHash(Map<String, Integer> stockSnapshot) {
        String snapshot = stockSnapshot.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining("&"));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(snapshot.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private void resolveBackorder(ChannelOrder order, String resolution) {
        order.setStatus("PENDING_RESOLUTION_" + resolution);
        channelOrders.saveAndFlush(order);
        sendResolution(order, resolution);
    }

    @Scheduled(fixedDelayString = "${tiangge.feed-poll-ms:3000}", initialDelay = 5000)
    void retryPendingResolutions() {
        if (!enabled()) return;
        for (ChannelOrder order : channelOrders.findAll()) {
            if (!order.getStatus().startsWith("PENDING_RESOLUTION_")) continue;
            sendResolution(order, order.getStatus().substring("PENDING_RESOLUTION_".length()));
        }
    }

    private void sendResolution(ChannelOrder order, String resolution) {
        try {
            http.post("/orders/" + order.getMarketplaceOrderId() + "/resolution", Map.of("status", resolution));
            order.setStatus(resolution);
            channelOrders.save(order);
            flushStockChanges("ACCEPTED".equals(resolution));
        } catch (Exception e) {
            log.warn("Tiangge resolution for {} will retry: {}", order.getMarketplaceOrderId(), e.getMessage());
        }
    }

    void processCancellation(String marketplaceOrderId) throws Exception {
        ChannelOrder order = channelOrders.findById(marketplaceOrderId).orElse(null);
        boolean alreadyRestocked = order != null && "CANCELLED_BY_CUSTOMER".equals(order.getStatus());
        boolean shouldRestock = order != null && order.getShopOrderId() != null && "ACCEPTED".equals(order.getStatus());
        boolean restocked = alreadyRestocked || shouldRestock;
        if (shouldRestock) {
            try { orders.cancelOrder(order.getShopOrderId()); }
            catch (org.springframework.web.server.ResponseStatusException alreadyHandled) {
                if (alreadyHandled.getStatusCode().value() != 409) throw alreadyHandled;
            }
        }
        http.post("/orders/" + marketplaceOrderId + "/cancellation", Map.of("restocked", restocked));
        if (order != null) { order.setStatus("CANCELLED_BY_CUSTOMER"); channelOrders.save(order); }
        flushStockChanges(restocked);
    }

    private void publishStock(List<InventoryItemView> items) {
        http.put("/stock", items.stream().map(i -> Map.of("sellerSku", i.productId(), "available", i.stock())).toList());
    }
    private boolean enabled() { return !properties.apiKey.isBlank() && !properties.clientId.isBlank(); }
}
