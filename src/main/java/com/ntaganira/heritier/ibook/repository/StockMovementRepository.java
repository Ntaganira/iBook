/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : StockMovementRepository.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for stock movements
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.StockMovement;
import com.ntaganira.heritier.ibook.enums.MovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    @Query("select m from StockMovement m where (:productId is null or m.productId = :productId) "
            + "and (:warehouseId is null or m.warehouseId = :warehouseId) "
            + "and (:type is null or m.movementType = :type) "
            + "and (:from is null or m.movementDate >= :from) "
            + "and (:to is null or m.movementDate <= :to)")
    Page<StockMovement> search(@Param("productId") Long productId,
                               @Param("warehouseId") Long warehouseId,
                               @Param("type") MovementType type,
                               @Param("from") LocalDate from,
                               @Param("to") LocalDate to,
                               Pageable pageable);

    List<StockMovement> findByProductIdOrderByMovementDateAscIdAsc(Long productId);

    /** Signed quantity on hand per product, as of a date. */
    @Query("select m.productId, "
            + "coalesce(sum(case when m.movementType in "
            + "  (com.ntaganira.heritier.ibook.enums.MovementType.OPENING, "
            + "   com.ntaganira.heritier.ibook.enums.MovementType.PURCHASE, "
            + "   com.ntaganira.heritier.ibook.enums.MovementType.ADJUSTMENT_IN, "
            + "   com.ntaganira.heritier.ibook.enums.MovementType.TRANSFER_IN) "
            + "  then m.quantity else -m.quantity end), 0) "
            + "from StockMovement m where m.movementDate <= :asOf group by m.productId")
    List<Object[]> onHandByProduct(@Param("asOf") LocalDate asOf);

    long countByProductId(Long productId);
}
