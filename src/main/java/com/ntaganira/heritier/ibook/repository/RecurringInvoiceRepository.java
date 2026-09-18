/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : RecurringInvoiceRepository.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for recurring invoice schedules
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.RecurringInvoice;
import com.ntaganira.heritier.ibook.enums.RecurringInvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RecurringInvoiceRepository extends JpaRepository<RecurringInvoice, Long> {

    Optional<RecurringInvoice> findByNameIgnoreCase(String name);

    @Query("select r from RecurringInvoice r where (:q is null or :q = '' "
            + "   or lower(r.name) like lower(concat('%', :q, '%')) "
            + "   or lower(r.customerName) like lower(concat('%', :q, '%')) "
            + "   or lower(r.reference) like lower(concat('%', :q, '%'))) "
            + "   and (:status is null or r.status = :status) "
            + "   and (:customerId is null or r.customerId = :customerId)")
    Page<RecurringInvoice> search(@Param("q") String q,
                                  @Param("status") RecurringInvoiceStatus status,
                                  @Param("customerId") Long customerId,
                                  Pageable pageable);

    long countByStatus(RecurringInvoiceStatus status);

    @Query("select coalesce(sum(r.total), 0) from RecurringInvoice r where r.status in :statuses")
    BigDecimal totalFor(@Param("statuses") List<RecurringInvoiceStatus> statuses);

    @Query("select count(r) from RecurringInvoice r where r.status = "
            + "com.ntaganira.heritier.ibook.enums.RecurringInvoiceStatus.ACTIVE "
            + "and r.nextRunDate <= :today")
    long countDue(@Param("today") LocalDate today);

    @Query("select r from RecurringInvoice r where r.status = "
            + "com.ntaganira.heritier.ibook.enums.RecurringInvoiceStatus.ACTIVE "
            + "and r.nextRunDate <= :today order by r.nextRunDate asc, r.id asc")
    List<RecurringInvoice> findDue(@Param("today") LocalDate today);

    @Query("select r from RecurringInvoice r where r.customerId = :customerId "
            + "order by r.nextRunDate asc")
    List<RecurringInvoice> findByCustomer(@Param("customerId") Long customerId);
}
