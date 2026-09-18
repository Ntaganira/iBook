/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : BillRepository.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for vendor bills
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.Bill;
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

public interface BillRepository extends JpaRepository<Bill, Long> {

    Optional<Bill> findByBillNo(String billNo);

    @Query("select b from Bill b where (:q is null or :q = '' "
            + "   or lower(b.billNo) like lower(concat('%', :q, '%')) "
            + "   or lower(b.vendorName) like lower(concat('%', :q, '%')) "
            + "   or lower(b.vendorInvoiceNo) like lower(concat('%', :q, '%')) "
            + "   or lower(b.reference) like lower(concat('%', :q, '%'))) "
            + "   and (:status is null or b.status = :status) "
            + "   and (:vendorId is null or b.vendorId = :vendorId) "
            + "   and (:from is null or b.billDate >= :from) "
            + "   and (:to is null or b.billDate <= :to)")
    Page<Bill> search(@Param("q") String q,
                      @Param("status") DocumentStatus status,
                      @Param("vendorId") Long vendorId,
                      @Param("from") LocalDate from,
                      @Param("to") LocalDate to,
                      Pageable pageable);

    long countByStatus(DocumentStatus status);

    @Query("select count(b) from Bill b where b.status in :statuses and b.dueDate < :today")
    long countOverdue(@Param("statuses") List<DocumentStatus> statuses, @Param("today") LocalDate today);

    @Query("select coalesce(sum(b.total - b.amountPaid), 0) from Bill b "
            + "where b.status in (com.ntaganira.heritier.ibook.enums.DocumentStatus.OPEN, "
            + "com.ntaganira.heritier.ibook.enums.DocumentStatus.PARTIALLY_PAID, "
            + "com.ntaganira.heritier.ibook.enums.DocumentStatus.OVERDUE)")
    BigDecimal totalOutstanding();

    @Query("select coalesce(sum(b.total - b.amountPaid), 0) from Bill b "
            + "where b.status in (com.ntaganira.heritier.ibook.enums.DocumentStatus.OPEN, "
            + "com.ntaganira.heritier.ibook.enums.DocumentStatus.PARTIALLY_PAID, "
            + "com.ntaganira.heritier.ibook.enums.DocumentStatus.OVERDUE) and b.dueDate < :today")
    BigDecimal totalOverdue(@Param("today") LocalDate today);

    @Query("select coalesce(sum(b.total), 0) from Bill b "
            + "where b.status <> com.ntaganira.heritier.ibook.enums.DocumentStatus.DRAFT "
            + "and b.status <> com.ntaganira.heritier.ibook.enums.DocumentStatus.VOID "
            + "and b.billDate >= :from and b.billDate <= :to")
    BigDecimal totalBilledBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select b from Bill b where b.vendorId = :vendorId order by b.billDate desc")
    List<Bill> findByVendor(@Param("vendorId") Long vendorId);

    @Query("select b from Bill b "
            + "where b.status <> com.ntaganira.heritier.ibook.enums.DocumentStatus.DRAFT "
            + "and b.status <> com.ntaganira.heritier.ibook.enums.DocumentStatus.VOID "
            + "and b.billDate >= :from and b.billDate <= :to order by b.billDate asc, b.id asc")
    List<Bill> findPostedBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select coalesce(sum(b.total - b.amountPaid), 0) from Bill b where b.vendorId = :vendorId "
            + "and b.status in (com.ntaganira.heritier.ibook.enums.DocumentStatus.OPEN, "
            + "com.ntaganira.heritier.ibook.enums.DocumentStatus.PARTIALLY_PAID)")
    BigDecimal vendorBalance(@Param("vendorId") Long vendorId);
}
