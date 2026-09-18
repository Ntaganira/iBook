/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : PurchaseOrderRepository.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for vendor purchase orders
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.PurchaseOrder;
import com.ntaganira.heritier.ibook.enums.PurchaseOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findByOrderNo(String orderNo);

    @Query("select p from PurchaseOrder p where (:q is null or :q = '' "
            + "   or lower(p.orderNo) like lower(concat('%', :q, '%')) "
            + "   or lower(p.vendorName) like lower(concat('%', :q, '%')) "
            + "   or lower(p.reference) like lower(concat('%', :q, '%'))) "
            + "   and (:status is null or p.status = :status) "
            + "   and (:vendorId is null or p.vendorId = :vendorId)")
    Page<PurchaseOrder> search(@Param("q") String q,
                               @Param("status") PurchaseOrderStatus status,
                               @Param("vendorId") Long vendorId,
                               Pageable pageable);

    long countByStatus(PurchaseOrderStatus status);

    @Query("select coalesce(sum(p.total), 0) from PurchaseOrder p where p.status in :statuses")
    BigDecimal totalFor(@Param("statuses") List<PurchaseOrderStatus> statuses);

    @Query("select count(p) from PurchaseOrder p where p.status = "
            + "com.ntaganira.heritier.ibook.enums.PurchaseOrderStatus.CONFIRMED "
            + "and p.expectedDate < :today")
    long countLate(@Param("today") LocalDate today);

    @Query("select p from PurchaseOrder p where p.vendorId = :vendorId order by p.orderDate desc")
    List<PurchaseOrder> findByVendor(@Param("vendorId") Long vendorId);
}
