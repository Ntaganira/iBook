/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : SalesOrderRepository.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for customer sales orders
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.SalesOrder;
import com.ntaganira.heritier.ibook.enums.SalesOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    Optional<SalesOrder> findByOrderNo(String orderNo);

    @Query("select o from SalesOrder o where (:q is null or :q = '' "
            + "   or lower(o.orderNo) like lower(concat('%', :q, '%')) "
            + "   or lower(o.customerName) like lower(concat('%', :q, '%')) "
            + "   or lower(o.reference) like lower(concat('%', :q, '%'))) "
            + "   and (:status is null or o.status = :status) "
            + "   and (:customerId is null or o.customerId = :customerId)")
    Page<SalesOrder> search(@Param("q") String q,
                            @Param("status") SalesOrderStatus status,
                            @Param("customerId") Long customerId,
                            Pageable pageable);

    long countByStatus(SalesOrderStatus status);

    @Query("select coalesce(sum(o.total), 0) from SalesOrder o where o.status in :statuses")
    BigDecimal totalFor(@Param("statuses") List<SalesOrderStatus> statuses);

    @Query("select count(o) from SalesOrder o where o.status = "
            + "com.ntaganira.heritier.ibook.enums.SalesOrderStatus.CONFIRMED "
            + "and o.expectedDate < :today")
    long countLate(@Param("today") LocalDate today);

    @Query("select o from SalesOrder o where o.customerId = :customerId order by o.orderDate desc")
    List<SalesOrder> findByCustomer(@Param("customerId") Long customerId);
}
