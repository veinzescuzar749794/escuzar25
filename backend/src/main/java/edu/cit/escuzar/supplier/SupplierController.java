package edu.cit.escuzar.supplier;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Package-private REST controller for viewing and triggering supplier orders.
 */
@RestController
@RequestMapping("/api/supplier")
class SupplierController {

    record SupplierOrderResponse(
            Long id,
            String productId,
            String buyerRef,
            String requestId,
            String poNumber,
            int cases,
            int units,
            SupplierOrderStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {}

    record ManualReorderRequest(String productId, int unitsNeeded) {}

    private final SupplierOrderRepository repository;
    private final SupplierGateway supplierGateway;

    SupplierController(SupplierOrderRepository repository, SupplierGateway supplierGateway) {
        this.repository = repository;
        this.supplierGateway = supplierGateway;
    }

    @GetMapping("/orders")
    public List<SupplierOrderResponse> listOrders() {
        return repository.findAll().stream()
                .map(o -> new SupplierOrderResponse(
                        o.getId(),
                        o.getProductId(),
                        o.getBuyerRef(),
                        o.getRequestId(),
                        o.getPoNumber(),
                        o.getCases(),
                        o.getUnits(),
                        o.getStatus(),
                        o.getCreatedAt(),
                        o.getUpdatedAt()
                ))
                .toList();
    }

    @PostMapping("/reorder")
    public ResponseEntity<SupplierOrderResult> triggerReorder(@RequestBody ManualReorderRequest req) {
        SupplierOrderResult result = supplierGateway.placeOrder(req.productId(), req.unitsNeeded());
        return ResponseEntity.ok(result);
    }
}
