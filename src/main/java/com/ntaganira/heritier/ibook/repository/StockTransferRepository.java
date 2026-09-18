/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : StockTransferRepository.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for stock transfers
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.StockTransfer;
import com.ntaganira.heritier.ibook.enums.TransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {

    Optional<StockTransfer> findByTransferNo(String transferNo);

    @Query("select t from StockTransfer t where (:q is null or :q = '' "
            + "   or lower(t.transferNo) like lower(concat('%', :q, '%')) "
            + "   or lower(t.fromWarehouseName) like lower(concat('%', :q, '%')) "
            + "   or lower(t.toWarehouseName) like lower(concat('%', :q, '%')) "
            + "   or lower(t.reference) like lower(concat('%', :q, '%'))) "
            + "   and (:status is null or t.status = :status) "
            + "   and (:warehouseId is null or t.fromWarehouseId = :warehouseId "
            + "        or t.toWarehouseId = :warehouseId)")
    Page<StockTransfer> search(@Param("q") String q,
                               @Param("status") TransferStatus status,
                               @Param("warehouseId") Long warehouseId,
                               Pageable pageable);

    long countByStatus(TransferStatus status);

    @Query("select coalesce(sum(t.totalValue), 0) from StockTransfer t where t.status = :status")
    BigDecimal valueFor(@Param("status") TransferStatus status);

    @Query("select coalesce(sum(t.shortfallValue), 0) from StockTransfer t where t.status = "
            + "com.ntaganira.heritier.ibook.enums.TransferStatus.COMPLETED")
    BigDecimal totalShortfallValue();

    @Query("select count(t) from StockTransfer t where t.status = "
            + "com.ntaganira.heritier.ibook.enums.TransferStatus.COMPLETED "
            + "and t.shortfallValue > 0")
    long countShort();

    @Query("select t from StockTransfer t where t.fromWarehouseId = :warehouseId "
            + "or t.toWarehouseId = :warehouseId order by t.transferDate desc")
    List<StockTransfer> findByWarehouse(@Param("warehouseId") Long warehouseId);
}
