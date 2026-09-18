/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : CreditNoteRepository.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for customer credit notes
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.CreditNote;
import com.ntaganira.heritier.ibook.enums.CreditNoteStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CreditNoteRepository extends JpaRepository<CreditNote, Long> {

    Optional<CreditNote> findByCreditNoteNo(String creditNoteNo);

    @Query("select c from CreditNote c where (:q is null or :q = '' "
            + "   or lower(c.creditNoteNo) like lower(concat('%', :q, '%')) "
            + "   or lower(c.customerName) like lower(concat('%', :q, '%')) "
            + "   or lower(c.invoiceNo) like lower(concat('%', :q, '%')) "
            + "   or lower(c.reference) like lower(concat('%', :q, '%'))) "
            + "   and (:status is null or c.status = :status) "
            + "   and (:customerId is null or c.customerId = :customerId) "
            + "   and (:from is null or c.creditDate >= :from) "
            + "   and (:to is null or c.creditDate <= :to)")
    Page<CreditNote> search(@Param("q") String q,
                            @Param("status") CreditNoteStatus status,
                            @Param("customerId") Long customerId,
                            @Param("from") LocalDate from,
                            @Param("to") LocalDate to,
                            Pageable pageable);

    long countByStatus(CreditNoteStatus status);

    @Query("select coalesce(sum(c.total), 0) from CreditNote c where c.status in :statuses")
    BigDecimal totalFor(@Param("statuses") List<CreditNoteStatus> statuses);

    @Query("select coalesce(sum(c.total), 0) from CreditNote c "
            + "where c.status <> com.ntaganira.heritier.ibook.enums.CreditNoteStatus.DRAFT "
            + "and c.status <> com.ntaganira.heritier.ibook.enums.CreditNoteStatus.VOID "
            + "and c.creditDate >= :from and c.creditDate <= :to")
    BigDecimal totalCreditedBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select coalesce(sum(c.total - coalesce(c.appliedAmount, 0)), 0) from CreditNote c "
            + "where c.status = com.ntaganira.heritier.ibook.enums.CreditNoteStatus.ISSUED")
    BigDecimal totalUnapplied();

    @Query("select c from CreditNote c where c.customerId = :customerId order by c.creditDate desc")
    List<CreditNote> findByCustomer(@Param("customerId") Long customerId);

    @Query("select c from CreditNote c where c.invoiceId = :invoiceId "
            + "and c.status <> com.ntaganira.heritier.ibook.enums.CreditNoteStatus.VOID "
            + "order by c.creditDate asc")
    List<CreditNote> findByInvoice(@Param("invoiceId") Long invoiceId);
}
