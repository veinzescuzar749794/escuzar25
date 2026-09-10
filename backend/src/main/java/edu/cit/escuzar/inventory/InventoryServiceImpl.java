package edu.cit.escuzar.inventory;

import edu.cit.escuzar.inventory.dto.InventoryItemView;
import edu.cit.escuzar.inventory.dto.ReservationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    InventoryServiceImpl(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InventoryItemView> getItem(String productId) {
        return inventoryRepository.findById(productId).map(this::toView);
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

        return new ReservationResult(true, null, toView(item));
    }

    private InventoryItemView toView(InventoryItem item) {
        return new InventoryItemView(item.getProductId(), item.getName(), item.getStock());
    }
}
