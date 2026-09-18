/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : InvoicePaymentRepository.java
 * - Date      : 2026. 09. 17.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for invoice payments
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.InvoicePayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface InvoicePaymentRepository extends JpaRepository<InvoicePayment, Long> {

    List<InvoicePayment> findByInvoiceIdOrderByPaymentDateAsc(Long invoiceId);

    @Query("select coalesce(sum(p.amount), 0) from InvoicePayment p where p.invoice.id = :invoiceId")
    BigDecimal totalPaidFor(@Param("invoiceId") Long invoiceId);

    @Query("select p from InvoicePayment p where (:q is null or :q = '' "
            + "   or lower(p.reference) like lower(concat('%', :q, '%')) "
            + "   or lower(p.invoice.invoiceNo) like lower(concat('%', :q, '%')) "
            + "   or lower(p.invoice.customerName) like lower(concat('%', :q, '%')))")
    Page<InvoicePayment> search(@Param("q") String q, Pageable pageable);

    @Query("select coalesce(sum(p.amount), 0) from InvoicePayment p where p.paymentDate >= :from and p.paymentDate <= :to")
    BigDecimal collectedBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select p from InvoicePayment p join fetch p.invoice i "
            + "where p.paymentDate >= :from and p.paymentDate <= :to "
            + "order by p.paymentDate desc, p.id desc")
    List<InvoicePayment> findBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select p from InvoicePayment p join fetch p.invoice i where i.customerId = :partyId "
            + "order by p.paymentDate asc, p.id asc")
    List<InvoicePayment> findByCustomer(@Param("partyId") Long partyId);
}
