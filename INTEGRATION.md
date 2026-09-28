# LegacySupply Integration Specification & Contract Discovery

This document details the contract discovery, protocol quirks, error catalog, session lifecycle, unit-of-measure conversions, and anti-corruption design for integrating with **LegacySupply**.

---

## 1. Inventory to LegacySupply Mapping Table

LegacySupply enforces its own proprietary item numbers (`SupplierSku`) and wholesale pack sizes (`PackSize`). The internal Inventory system uses domain-specific product identifiers (`productId`).

| Inventory Product ID | Internal Product Name | LegacySupply `SupplierSku` | Wholesale `PackSize` | Description in Catalog | Wholesale Unit Cost |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `P100` | Wireless Mouse | `WQC-6004` | 12 units / case | WIRELESS MOUSE 2.4GHZ | 450.00 PHP |
| `P200` | Mechanical Keyboard | `WQC-9692` | 24 units / case | KEYBOARD MECH TKL | 1,899.00 PHP |
| `P300` | USB-C Hub | `WQC-2303` | 12 units / case | USB HUB 4-PORT | 399.00 PHP |

---

## 2. LegacySupply Session Lifecycle & Empirical Measurement

### How Sessions Work
1. **Authentication Endpoint**: `POST /api/v1/auth/token`
   - Accepts an XML payload containing `<AuthRequest><ClientId>...</ClientId><ApiKey>...</ApiKey></AuthRequest>`.
   - On success (HTTP 200), returns an XML document:
     ```xml
     <AuthResponse>
       <SessionToken>5b40b4ffc08837f9e19574da435ab904b7c5</SessionToken>
       <IssuedAt>2026-09-28T10:42:24.157Z</IssuedAt>
     </AuthResponse>
     ```
2. **Authenticated Calls**:
   - The token must be transmitted in the `X-LS-Session` HTTP header on all subsequent calls (`/catalog`, `/purchase-orders`, `/purchase-orders/{PoNumber}`, `/purchase-orders?buyerRef=...`).
3. **Session Expiration**:
   - When a session expires, LegacySupply returns `HTTP 401 Unauthorized` with an error XML body:
     ```xml
     <LSError>
       <Code>E-AUTH-07</Code>
       <Message>Session not valid.</Message>
     </LSError>
     ```

### Empirical Measurement of Session Lifetime
The official manual states that sessions are "short-lived" without specifying the TTL. Through empirical testing with controlled interval probing:
- A token issued at `10:42:24.157Z` was probed at **110 seconds**: Returned `HTTP 200 OK`.
- The same token probed at **125 seconds**: Returned `HTTP 401 Unauthorized` with `E-AUTH-07 Session not valid`.
- Conclusion: **LegacySupply sessions expire exactly after 120 seconds (2 minutes)**.

### Adapter Session Management Strategy
The Anti-Corruption Layer handles session renewal automatically:
- Caches the active `SessionToken` and its acquisition timestamp in memory.
- Proactively: If a cached token is older than 100 seconds (giving a 20-second safety margin), the adapter acquires a fresh token before dispatching the request.
- Reactively: If any call encounters `HTTP 401` with `E-AUTH-07` or `E-AUTH-03`, the cached session is immediately invalidated, re-authentication is performed, and the request is transparently retried.

---

## 3. Error Codes Observed & Verified

During discovery and probing against `https://legacysupply.onrender.com/api/v1`, the following error codes were observed and verified with live curl requests:

| Error Code | HTTP Status | Message | Actual Cause Triggered |
| :--- | :--- | :--- | :--- |
| `E-AUTH-01` | 401 | `Credentials rejected.` | Sent an invalid API key (`LSK-WRONGKEY`) or unknown `ClientId` to `/auth/token`. |
| `E-AUTH-02` | 401 | `Session header missing.` | Attempted to access an authenticated endpoint (`/catalog`) without the `X-LS-Session` header. |
| `E-AUTH-03` | 401 | `Session not recognized.` | Supplied a random, unrecognized string as `X-LS-Session`. |
| `E-AUTH-07` | 401 | `Session not valid.` | Attempted to make a request with an expired session token (>120 seconds after issue). |
| `E-FMT-01` | 415 | `Unsupported media.` | Dispatched request with `Content-Type: application/json` instead of required `application/xml`. |
| `E-FMT-02` | 400 | `Malformed document.` | Sent an unclosed or syntactically invalid XML payload (`<PurchaseOrder><unclosed>`). |
| `E-REF-05` | 400 | `BuyerRef invalid.` | Sent an empty `<BuyerRef></BuyerRef>` or one exceeding 40 characters in a purchase order request. |
| `E-SKU-02` | 422 | `Item not recognized.` | Requested an invalid or non-existent supplier SKU (`INVALID-SKU`). |
| `E-QTY-11` | 422 | `Quantity invalid.` | Sent an order with `<Qty>0</Qty>` or `<Qty>100</Qty>` (valid range is 1 to 99 cases). |
| `E-PO-04` | 404 | `Order not found.` | Queried `/purchase-orders/{PoNumber}` with a non-existent PO number (`PO-99999999`). |
| `E-QRY-06` | 400 | `Query parameter required.` | Queried `/purchase-orders` without the required `?buyerRef=` parameter. |
| `E-SYS-50` | 503 | `Processing error.` | Server-side transient processing failure injected by chaos simulation. |
| `E-RATE-03` | 429 | `Request quota exceeded.` | Exceeded allowed request rate (polling too aggressively). |
| `E-IDEM-04` | 409 | `Request id reused with different content.` | Reused the same `X-Request-Id` header on a request with different body contents. |

---

## 4. Understanding `Qty` and `Uom` (Units of Measure)

### Definitions
- **`Qty` (Quantity)**: In LegacySupply's contract, `Qty` is **always an integer number of cases**, never individual retail units. The allowable range is `1` to `99`.
- **`Uom` (Unit of Measure)**: In LegacySupply's system, `Uom` is `"CS"` (Cases). LegacySupply deals exclusively in wholesale case packs and does not ship loose retail pieces.
- In our internal **Inventory** module, stock is tracked strictly in **individual retail pieces/units**.

### Conversion & Rounding Rule
When internal Inventory requires $U_{\text{needed}}$ units of a product having pack size $P_{\text{pack}}$:
$$\text{Cases to Order } (Q) = \left\lceil \frac{U_{\text{needed}}}{P_{\text{pack}}} \right\rceil = \frac{U_{\text{needed}} + P_{\text{pack}} - 1}{P_{\text{pack}}}$$
$$\text{Units Received on Delivery} = Q \times P_{\text{pack}}$$

### Worked Example
1. **Internal Need**:
   - Product: `P100` (*Wireless Mouse*).
   - Remaining stock: `3` units (below threshold `5`).
   - The auto-reorder rule calculates that `17` units are needed to restore inventory to target levels.
2. **Translation via Anti-Corruption Layer**:
   - Internal Product `P100` maps to LegacySupply SKU `WQC-6004` with wholesale `PackSize = 12`.
   - Cases needed: $\lceil 17 / 12 \rceil = 2$ cases.
   - Outbound purchase order payload:
     ```xml
     <PurchaseOrder>
       <SupplierSku>WQC-6004</SupplierSku>
       <Qty>2</Qty>
       <BuyerRef>RO-1</BuyerRef>
     </PurchaseOrder>
     ```
3. **Supplier Fulfillment**:
   - LegacySupply acknowledges 2 cases (`Uom = CS`).
   - Total retail units contained in the shipment: $2 \times 12 = 24$ units.
4. **Delivery & Restock**:
   - When order tracking observes status code `40` (*Delivered*), the adapter publishes `SupplierOrderDeliveredEvent("P100", 24)`.
   - Inventory listens to this event and calls `inventoryService.restock("P100", 24)`.
   - Final stock becomes: $3 + 24 = 27$ units.

---

## 5. Status Codes and Handling of Unexpected Statuses

### LegacySupply Status Codes
- `10`: **Accepted** (Mapped to internal `ACCEPTED`)
- `20`: **Picking** (Mapped to internal `PICKING`)
- `30`: **Shipped** (Mapped to internal `SHIPPED`)
- `40`: **Delivered** (Mapped to internal `DELIVERED` - triggers stock replenishment)
- `50` or `90`: **Cancelled** (Empirically verified on `PO-100327`; mapped to internal `CANCELLED`)

### Handling Unexpected / Unknown Statuses (Part E Requirement)
If LegacySupply returns an unexpected status code (e.g., an unannounced code such as `99`, `HOLD`, or a negative code):
1. **Resilience & Mapping**: The adapter does not crash or throw unhandled exceptions. Instead, it maps unrecognized codes to `SupplierOrderStatus.UNKNOWN`.
2. **Audit Logging & Alerting**: A warning is logged with the raw status code, PO number, and buyer reference. An administrative domain notification is emitted.
3. **Inventory Isolation**: Inventory is **not** restocked until verified.
4. **Retry / Review**: The order remains recorded with status `UNKNOWN` in `supplier_orders` so operators can inspect or reconcile with supplier support without losing track of the transaction.
