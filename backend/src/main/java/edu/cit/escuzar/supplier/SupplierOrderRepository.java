package edu.cit.escuzar.supplier;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Package-private Spring Data repository for SupplierOrder entities.
 */
interface SupplierOrderRepository extends JpaRepository<SupplierOrder, Long> {
    boolean existsByProductIdAndStatusIn(String productId, java.util.Collection<SupplierOrderStatus> statuses);

    Optional<SupplierOrder> findByRequestId(String requestId);

    Optional<SupplierOrder> findByBuyerRef(String buyerRef);

    List<SupplierOrder> findByStatus(SupplierOrderStatus status);

    List<SupplierOrder> findByStatusIn(Collection<SupplierOrderStatus> statuses);
}
