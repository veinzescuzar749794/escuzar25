# Lab 3: LegacySupply Integration - Reflection

This document contains the reflections required by Part F, answered using empirical evidence from our live integration logs and the architecture implemented in `edu.cit.escuzar.supplier`.

---

### Question 1
> **LegacySupply holds more than one order for BuyerRef "RO-PROBE-3": PO-100327 (19:25:40) and PO-100328 (19:26:19). Reconstruct the sequence of events that produced the duplicate, and describe the change you made (or would make) so it cannot happen again.**

#### Response:
During probe testing at 19:25:40, an initial order request for BuyerRef `"RO-PROBE-3"` was dispatched with an ephemeral UUID (`2c5d14a9-1bc4-43ee-aa45-9ecda15a709e`). Although LegacySupply accepted and recorded `PO-100327` internally, the server injected a transient chaos error (`HTTP 503 E-SYS-50 Processing error`) on the HTTP return path before delivering the `201 PurchaseOrderAck`. At 19:26:19, a subsequent manual probe was transmitted with a freshly generated `X-Request-Id` (`REQ-PROBE-ORDER-3-UNIQUE`) instead of preserving the original UUID; because the idempotency key differed, LegacySupply treated it as a distinct purchase order and created duplicate order `PO-100328`. To permanently prevent this condition, our Anti-Corruption Layer in `SupplierGatewayImpl` writes every reorder to the `supplier_orders` database table first with a persistent `request_id` and unique `buyer_ref` before initiating any network transmission. Furthermore, `SupplierOrderScheduler.retryPendingOrders()` performs a pre-flight verification against `GET /purchase-orders?buyerRef={BuyerRef}` before retrying, adopting any existing PO number on file and guaranteeing that duplicate orders cannot be created across network failures or process restarts.

---

### Question 2
> **At 19:25:40 your request for BuyerRef "RO-PROBE-3" (X-Request-Id 2c5d14a9-1bc4-43ee-aa45-9ecda15a709e) received a 503, but LegacySupply had already created PO-100327. Walk through exactly what your adapter did next, and explain why that did or did not result in a second order.**

#### Response:
When the 503 response was received at 19:25:40, the resilient client in `LegacySupplyHttpClient` caught the `E-SYS-50` failure and exhausted its immediate exponential backoff retry attempts while the supplier remained unavailable. Instead of losing the reorder or throwing an unhandled exception to the Inventory caller, `SupplierGatewayImpl` caught the failure and retained the order in the database with status `PENDING`. On the subsequent execution cycle of `SupplierOrderScheduler.retryPendingOrders()`, the scheduler evaluated the pending reorder and invoked `LegacySupplyHttpClient.findOrderByBuyerRef("RO-PROBE-3")` prior to attempting an outbound POST. Because LegacySupply had already created `PO-100327` during the initial attempt, this query returned the existing order record with status `10 (Accepted)`. Consequently, the adapter simply updated the local database record to `SupplierOrderStatus.ACCEPTED` with `poNumber = "PO-100327"` without issuing a secondary purchase order POST, successfully preventing duplication.

---

### Question 3
> **PO-100327 (BuyerRef "RO-PROBE-3") ended with StatusCode 90, which is not in the documentation. How did you work out what it means, and what does your system now do with the stock that will never arrive?**

#### Response:
We deduced that StatusCode `90` represents a cancelled order by correlating our polling requests with the real-time checklist on LegacySupply's self-check page (`/verify/api`); prior to polling `PO-100327`, the checklist recorded `0 cancelled orders seen`, but immediately upon our client receiving `StatusCode 90`, the verification metric satisfied the requirement with `1 cancelled orders seen (True)`. In response to this unlisted status, our adapter's status translator (`SupplierGatewayImpl.mapStatusCode`) maps code `90` directly to `SupplierOrderStatus.CANCELLED` and persists the updated status in the `supplier_orders` audit table. Crucially, our architecture isolates Inventory from supplier failures by requiring a `SupplierOrderDeliveredEvent` before any stock is added; because a cancelled order never emits this delivery event, no phantom stock is restocked into the system. As a result, the physical inventory remains at its actual low-stock level, triggering the low-stock auto-reorder rule on subsequent cycles to place a replacement order while notifying system administrators of the cancellation.

---

# Lab 4: Tiangge Marketplace Reflection

These responses use the live verification record and the persisted order rows for `TG-FVYDCL`.

### Question 1: Duplicate feed delivery and restart safety

> Event `evt_010165092bdd43a4` for order `TG-FVYDCL` reached the application at feed sequences 1 and 10, but was processed once. Show the safeguards and explain what happens across a restart.

The adapter uses `marketplace_order_id` as the primary key of `tiangge_orders`. `ChannelService.processNewOrder()` checks that table before creating a shop order. A completed row is left alone on redelivery; only `PROCESSING` rows resume local order creation, and `PENDING_DECISION_*` rows retry the marketplace decision. The associated shop order also has a unique `source_reference`, so a replay after the local order was saved but before its marketplace decision completed returns the same shop order instead of reserving stock twice.

Both the mapping row and the feed cursor are stored in Supabase (`tiangge_orders` and `tiangge_checkpoint`). For this delivery, the stored row is `TG-FVYDCL`, status `ACCEPTED`, shop order `35`; the shop order is `CONFIRMED` and has a fulfillment source reference. If the process restarts between deliveries, the second delivery finds the persisted mapping and does not create another order. If it restarts while the mapping is still `PROCESSING`, the unique source reference makes resuming safe. The cursor advances only after an event has been processed.

### Question 2: Supplier delivery resumes the backorder

> `TG-FVYDCL` was backordered at 21:09:31 and accepted at 21:15:39 after PO-101889 was delivered at 21:15:30. Trace delivery through Inventory and backorder fulfillment.

`SupplierOrderScheduler.trackOpenOrders()` polls open supplier orders and, when LegacySupply reports `DELIVERED`, publishes `SupplierOrderDeliveredEvent` with the persisted product and units. `InventoryEventListener` receives that domain event and calls the Inventory module's `restock()` method. Inventory persists the added units and emits `InventoryStockChangedEvent`.

The Tiangge adapter sends the stock change and checks affected backorders. `resolveReadyBackorders()` waits until every requested product has enough stock, then re-enters `OrderService.placeOrder()` using an idempotent fulfillment source reference. The order shown in the live record contains six units of `P300`; Supabase records the delivered PO-101889 as 12 units for P300 and maps the marketplace order to confirmed shop order 35. The adapter then sends the `ACCEPTED` resolution to Tiangge.

### Question 3: Missing P300 stock update and correction

> The backorder was accepted at 21:15:39, but Tiangge did not receive a P300 stock update within 30 seconds. What triggers updates, and why was one missed?

Inventory triggers a stock update by publishing `InventoryStockChangedEvent` after each successful `reserve()` or `restock()`. The original adapter held these events in a per-SKU map and its scheduled flush returned while any marketplace order was `PROCESSING` or awaiting a decision. Backorder fulfillment also did not force a flush after Tiangge accepted the resolution. That global gate could leave the P300 event queued beyond the marketplace deadline while other orders were being decided.

I changed the adapter to preserve stock-change events in order, retry failed batches without losing their order, and force a flush after an accepted marketplace decision or resolution and after a cancellation that restocked inventory. Unit tests cover repeated updates for one SKU and immediate flushing after acceptance. This fixes the identified delay path; the historical late-update count on the verification record remains recorded.
