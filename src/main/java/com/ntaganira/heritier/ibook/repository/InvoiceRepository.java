/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : InvoiceRepository.java
 * - Date      : 2026. 09. 17.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for sales invoices
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.Invoice;
import com.ntaganira.heritier.ibook.enums.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByInvoiceNo(String invoiceNo);

    List<Invoice> findByRecurringInvoiceIdOrderByIssueDateDescIdDesc(Long recurringInvoiceId);

    @Query("select i from Invoice i where (:q is null or :q = '' "
            + "   or lower(i.invoiceNo) like lower(concat('%', :q, '%')) "
            + "   or lower(i.customerName) like lower(concat('%', :q, '%')) "
            + "   or lower(i.reference) like lower(concat('%', :q, '%'))) "
            + "   and (:status is null or i.status = :status) "
            + "   and (:customerId is null or i.customerId = :customerId) "
            + "   and (:from is null or i.issueDate >= :from) "
            + "   and (:to is null or i.issueDate <= :to)")
    Page<Invoice> search(@Param("q") String q,
                         @Param("status") DocumentStatus status,
                         @Param("customerId") Long customerId,
                         @Param("from") LocalDate from,
                         @Param("to") LocalDate to,
                         Pageable pageable);

    @Query("select i from Invoice i where i.status in :statuses and i.dueDate < :today order by i.dueDate asc")
    List<Invoice> findOverdue(@Param("statuses") List<DocumentStatus> statuses, @Param("today") LocalDate today);

    long countByStatus(DocumentStatus status);

    @Query("select count(i) from Invoice i where i.status in :statuses and i.dueDate < :today")
    long countOverdue(@Param("statuses") List<DocumentStatus> statuses, @Param("today") LocalDate today);

    @Query("select coalesce(sum(i.total - i.amountPaid - coalesce(i.creditedAmount, 0)), 0) from Invoice i "
            + "where i.status in (com.ntaganira.heritier.ibook.enums.DocumentStatus.OPEN, "
            + "com.ntaganira.heritier.ibook.enums.DocumentStatus.PARTIALLY_PAID, "
            + "com.ntaganira.heritier.ibook.enums.DocumentStatus.OVERDUE)")
    BigDecimal totalOutstanding();

    @Query("select coalesce(sum(i.total - i.amountPaid - coalesce(i.creditedAmount, 0)), 0) from Invoice i "
            + "where i.status in (com.ntaganira.heritier.ibook.enums.DocumentStatus.OPEN, "
            + "com.ntaganira.heritier.ibook.enums.DocumentStatus.PARTIALLY_PAID, "
            + "com.ntaganira.heritier.ibook.enums.DocumentStatus.OVERDUE) and i.dueDate < :today")
    BigDecimal totalOverdue(@Param("today") LocalDate today);

    @Query("select coalesce(sum(i.total), 0) from Invoice i "
            + "where i.status <> com.ntaganira.heritier.ibook.enums.DocumentStatus.DRAFT "
            + "and i.status <> com.ntaganira.heritier.ibook.enums.DocumentStatus.VOID "
            + "and i.issueDate >= :from and i.issueDate <= :to")
    BigDecimal totalInvoicedBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select i from Invoice i where i.customerId = :customerId order by i.issueDate desc")
    List<Invoice> findByCustomer(@Param("customerId") Long customerId);

    @Query("select i from Invoice i "
            + "where i.status <> com.ntaganira.heritier.ibook.enums.DocumentStatus.DRAFT "
            + "and i.status <> com.ntaganira.heritier.ibook.enums.DocumentStatus.VOID "
            + "and i.issueDate >= :from and i.issueDate <= :to order by i.issueDate asc, i.id asc")
    List<Invoice> findPostedBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select coalesce(sum(i.total - i.amountPaid - coalesce(i.creditedAmount, 0)), 0) from Invoice i "
            + "where i.customerId = :customerId "
            + "and i.status in (com.ntaganira.heritier.ibook.enums.DocumentStatus.OPEN, "
            + "com.ntaganira.heritier.ibook.enums.DocumentStatus.PARTIALLY_PAID)")
    BigDecimal customerBalance(@Param("customerId") Long customerId);
}
