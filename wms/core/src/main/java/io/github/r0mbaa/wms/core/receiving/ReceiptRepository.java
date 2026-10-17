package io.github.r0mbaa.wms.core.receiving;

import io.github.r0mbaa.wms.core.topology.Warehouse;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    @EntityGraph(attributePaths = {"lines", "lines.sku", "warehouse"})
    Optional<Receipt> findByNumber(String number);

    boolean existsByNumber(String number);

    @EntityGraph(attributePaths = {"lines", "lines.sku", "warehouse"})
    List<Receipt> findByWarehouseAndStatusInOrderByCreatedAtDesc(Warehouse warehouse,
            List<ReceiptStatus> statuses);
}
