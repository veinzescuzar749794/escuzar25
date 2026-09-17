package edu.cit.escuzar.inventory;

import edu.cit.escuzar.inventory.dto.InventoryItemView;
import edu.cit.escuzar.inventory.dto.ReservationResult;
import edu.cit.escuzar.inventory.event.LowStockEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Package-private on purpose (no "public" modifier). This is the
 * enforced module boundary: code outside edu.cit.escuzar.inventory
 * physically cannot import this class -- it can only see it through
 * the InventoryService interface, which Spring wires in via
 * constructor injection. The compiler, not just convention, keeps the
 * Order module honest.
 */
@Service
class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final int lowStockThreshold;

    InventoryServiceImpl(
            InventoryRepository inventoryRepository,
            ApplicationEventPublisher eventPublisher,
            @Value("${app.inventory.low-stock-threshold:5}") int lowStockThreshold
    ) {
        this.inventoryRepository = inventoryRepository;
        this.eventPublisher = eventPublisher;
        this.lowStockThreshold = lowStockThreshold;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InventoryItemView> getItem(String productId) {
        return inventoryRepository.findById(productId).map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryItemView> getAllItems() {
        return inventoryRepository.findAll(Sort.by(Sort.Direction.ASC, "productId"))
                .stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional
    public ReservationResult reserve(String productId, int quantity) {
        Optional<InventoryItem> maybeItem = inventoryRepository.findByProductId(productId);

        if (maybeItem.isEmpty()) {
            return new ReservationResult(false, "Product " + productId + " does not exist", null);
        }

        InventoryItem item = maybeItem.get();

        if (quantity <= 0) {
            return new ReservationResult(false, "Quantity must be greater than zero", toView(item));
        }

        if (quantity > item.getStock()) {
            return new ReservationResult(
                    false,
                    "Requested quantity (" + quantity + ") exceeds available stock (" + item.getStock() + ")",
                    toView(item)
            );
        }

        item.setStock(item.getStock() - quantity);
        inventoryRepository.save(item);

        if (item.getStock() < lowStockThreshold) {
            eventPublisher.publishEvent(new LowStockEvent(
                    item.getProductId(),
                    item.getName(),
                    item.getStock(),
                    lowStockThreshold
            ));
        }

        return new ReservationResult(true, null, toView(item));
    }

    @Override
    @Transactional
    public void restock(String productId, int quantity) {
        if (quantity <= 0) {
            return;
        }

        Optional<InventoryItem> maybeItem = inventoryRepository.findByProductId(productId);
        if (maybeItem.isPresent()) {
            InventoryItem item = maybeItem.get();
            item.setStock(item.getStock() + quantity);
            inventoryRepository.save(item);
        }
    }

    private InventoryItemView toView(InventoryItem item) {
        return new InventoryItemView(item.getProductId(), item.getName(), item.getStock());
    }
}
