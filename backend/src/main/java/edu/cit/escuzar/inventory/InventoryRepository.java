package edu.cit.escuzar.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;

/**
 * Package-private on purpose: only InventoryServiceImpl (same package)
 * should ever touch the repository directly. Nothing outside the
 * inventory module can reach the database layer.
 */
interface InventoryRepository extends JpaRepository<InventoryItem, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<InventoryItem> findByProductId(String productId);
}
