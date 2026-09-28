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
