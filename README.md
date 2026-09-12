# Modular Monolith: Order + Inventory (Spring Boot + React + Supabase)

> The package root is `edu.cit.escuzar`. If `escuzar` is not your surname, replace
> it project-wide (including directory paths and the Maven `groupId`) before
> submitting.

## Architecture

```
backend/
  src/main/java/edu/cit/escuzar/
    ShopApplication.java        <- @SpringBootApplication, scans both modules
    CorsConfig.java              <- shared infra, not part of either module
    shop/                        <- Order module
      Order.java, OrderRepository.java, OrderService.java, OrderController.java
      dto/OrderRequest.java, dto/OrderResponse.java
    inventory/                   <- Inventory module
      InventoryItem.java, InventoryRepository.java
      InventoryService.java          <- public interface (the module boundary)
      InventoryServiceImpl.java      <- package-private (cannot leave this package)
      dto/InventoryItemView.java, dto/ReservationResult.java
frontend/                        <- Vite + React
sql/schema.sql                   <- table creation + seed data
```

The Order module depends only on `InventoryService` (interface) and the two
DTOs in `edu.cit.escuzar.inventory.dto`. `InventoryServiceImpl` and
`InventoryRepository` are package-private, so `edu.cit.escuzar.shop` code
literally cannot import them — the boundary is enforced by the Java
compiler, not just by convention.

## Supabase setup

1. Create a free project at [supabase.com](https://supabase.com).
2. In the Supabase dashboard, open **SQL Editor**, paste the contents of
   `sql/schema.sql`, and run it. This creates `inventory` and `orders` and
   seeds `inventory` with P100 (25), P200 (10), P300 (0).
3. Go to **Project Settings -> Database -> Connection string -> JDBC** and
   copy the connection string, username, and password.
4. Copy `backend/.env.example` to `backend/.env` (or export the variables
   in your shell) and fill in `SUPABASE_DB_URL`, `SUPABASE_DB_USERNAME`,
   `SUPABASE_DB_PASSWORD`. **Do not commit `.env`** — it's gitignored.

## Running it

**Backend**
```bash
cd backend
export $(cat .env | xargs)   # or set the three env vars another way
mvn spring-boot:run
```
Server starts on `http://localhost:8080`.

This project expects Maven 3.6+ and Java 17+ to be installed. Run
`mvn test` from `backend` to execute the confirmed and rejected service-path
tests before doing the browser-based end-to-end check below.

**Frontend**
```bash
cd frontend
npm install
npm run dev
```
Dev server starts on `http://localhost:5173`.

## Testing the two paths

- **Confirmed:** select P100 (stock 25), quantity 1, submit — response
  should be `{"status":"CONFIRMED", "inventory": {"stock": 24, ...}}`.
- **Rejected:** select P300 (stock 0), any quantity, submit — response
  should be `{"status":"REJECTED", "reason": "Requested quantity ... exceeds available stock (0)"}`.

## Network tab evidence

*(Fill in after running locally: open DevTools -> Network, filter on
`orders`, submit each case above, and paste/screenshot the request and
response for both.)*

**Confirmed order**
```
<img width="1920" height="1035" alt="{535643B6-51BE-4BD1-9402-72A3C4445216}" src="https://github.com/user-attachments/assets/99146631-ece5-4b01-86dd-43ef18121a38" />

```

**Rejected order**
```
<img width="1916" height="1036" alt="{D8DA70AC-318A-4A63-8161-D20E57BAD95D}" src="https://github.com/user-attachments/assets/be45a06d-1e47-4914-99aa-343178957472" />

```

## Reflection

**1. In-process vs. separate microservices.** Right now, `OrderService`
calling `InventoryService.reserve()` is a plain Java method call inside
the same JVM, in the same HTTP request thread, and — because both writes
happen through the same `EntityManager` — inside the same database
transaction. If the reservation fails partway through, the whole thing
rolls back atomically, for free. I also get compile-time type checking,
no serialization cost, and no network failure modes to think about.
If I split Inventory into its own service, I'd have to add back: an
HTTP or gRPC client with timeouts and retries, a circuit breaker for
when Inventory is down, a strategy for partial failure (the order call
succeeds but the inventory call times out — did it reserve or not?),
and either a distributed transaction/saga pattern or an eventual-
consistency model with compensating actions, since I can no longer wrap
both writes in one ACID transaction. I'd also need service discovery,
network auth between services, and independent deployment/observability
for each service.

**2. Why package-private matters.** Making `InventoryServiceImpl`
package-private means the compiler — not a code review comment —
enforces that nothing outside `edu.cit.escuzar.inventory` can new it up,
call implementation-specific methods, or cast an `InventoryService`
reference back down to the concrete class. `OrderService` is forced to
depend on the interface and the DTOs, which is exactly the shape of
dependency that would still work if Inventory later became a remote
service behind an HTTP client. If `InventoryServiceImpl` were public,
nothing would stop `OrderService` (or any future module) from importing
it directly, bypassing the interface, or reaching into
`InventoryRepository`. That coupling would be invisible until someone
tried to extract the module and discovered half the codebase depended
on inventory's internals.

**3. When to extract Inventory.** I'd extract it once Inventory needs to
scale, deploy, or fail independently of Order — for example, if a
separate team owns it, if it needs to serve other consumers besides
Order (a warehouse app, a supplier feed), or if its load pattern is very
different from Order's. Because the code already programs to the
`InventoryService` interface, the change is mostly additive: swap
`InventoryServiceImpl` for an HTTP-client implementation of the same
interface, move `inventory`/`InventoryItem` into their own database,
add retries/timeouts/circuit-breaking, replace the single DB transaction
with a saga or compensating-transaction flow, and version the new REST
contract. `OrderService` itself wouldn't need to change at all.
