/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : BillPaymentRepository.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for bill payments
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.BillPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface BillPaymentRepository extends JpaRepository<BillPayment, Long> {

    List<BillPayment> findByBillIdOrderByPaymentDateAsc(Long billId);

    @Query("select coalesce(sum(p.amount), 0) from BillPayment p where p.bill.id = :billId")
    BigDecimal totalPaidFor(@Param("billId") Long billId);

    @Query("select coalesce(sum(p.amount), 0) from BillPayment p where p.paymentDate >= :from and p.paymentDate <= :to")
    BigDecimal paidBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select p from BillPayment p join fetch p.bill b "
            + "where p.paymentDate >= :from and p.paymentDate <= :to "
            + "order by p.paymentDate desc, p.id desc")
    List<BillPayment> findBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select p from BillPayment p join fetch p.bill b where b.vendorId = :partyId "
            + "order by p.paymentDate asc, p.id asc")
    List<BillPayment> findByVendor(@Param("partyId") Long partyId);
}
