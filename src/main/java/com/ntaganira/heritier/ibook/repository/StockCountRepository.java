/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : StockCountRepository.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for physical stock counts
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.StockCount;
import com.ntaganira.heritier.ibook.enums.StockCountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface StockCountRepository extends JpaRepository<StockCount, Long> {

    Optional<StockCount> findByCountNo(String countNo);

    @Query("select c from StockCount c where (:q is null or :q = '' "
            + "   or lower(c.countNo) like lower(concat('%', :q, '%')) "
            + "   or lower(c.warehouseName) like lower(concat('%', :q, '%')) "
            + "   or lower(c.countedBy) like lower(concat('%', :q, '%')) "
            + "   or lower(c.reference) like lower(concat('%', :q, '%'))) "
            + "   and (:status is null or c.status = :status) "
            + "   and (:warehouseId is null or c.warehouseId = :warehouseId)")
    Page<StockCount> search(@Param("q") String q,
                            @Param("status") StockCountStatus status,
                            @Param("warehouseId") Long warehouseId,
                            Pageable pageable);

    long countByStatus(StockCountStatus status);

    @Query("select coalesce(sum(c.surplusValue), 0) from StockCount c where c.status = "
            + "com.ntaganira.heritier.ibook.enums.StockCountStatus.COMPLETED")
    BigDecimal totalSurplusValue();

    @Query("select coalesce(sum(c.shortageValue), 0) from StockCount c where c.status = "
            + "com.ntaganira.heritier.ibook.enums.StockCountStatus.COMPLETED")
    BigDecimal totalShortageValue();

    @Query("select c from StockCount c where c.warehouseId = :warehouseId "
            + "and c.status = com.ntaganira.heritier.ibook.enums.StockCountStatus.DRAFT")
    List<StockCount> findOpenAt(@Param("warehouseId") Long warehouseId);
}
