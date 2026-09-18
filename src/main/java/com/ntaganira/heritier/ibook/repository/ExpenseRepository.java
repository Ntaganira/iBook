/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : ExpenseRepository.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for direct expenses
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.Expense;
import com.ntaganira.heritier.ibook.enums.ExpenseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    Optional<Expense> findByExpenseNo(String expenseNo);

    @Query("select e from Expense e where (:q is null or :q = '' "
            + "   or lower(e.expenseNo) like lower(concat('%', :q, '%')) "
            + "   or lower(e.payeeName) like lower(concat('%', :q, '%')) "
            + "   or lower(e.reference) like lower(concat('%', :q, '%')) "
            + "   or lower(e.memo) like lower(concat('%', :q, '%'))) "
            + "   and (:status is null or e.status = :status) "
            + "   and (:vendorId is null or e.vendorId = :vendorId) "
            + "   and (:from is null or e.expenseDate >= :from) "
            + "   and (:to is null or e.expenseDate <= :to)")
    Page<Expense> search(@Param("q") String q,
                         @Param("status") ExpenseStatus status,
                         @Param("vendorId") Long vendorId,
                         @Param("from") LocalDate from,
                         @Param("to") LocalDate to,
                         Pageable pageable);

    long countByStatus(ExpenseStatus status);

    @Query("select coalesce(sum(e.total), 0) from Expense e where e.status = "
            + "com.ntaganira.heritier.ibook.enums.ExpenseStatus.POSTED")
    BigDecimal totalPosted();

    @Query("select coalesce(sum(e.total), 0) from Expense e where e.status = "
            + "com.ntaganira.heritier.ibook.enums.ExpenseStatus.POSTED "
            + "and e.expenseDate >= :from and e.expenseDate <= :to")
    BigDecimal totalBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select coalesce(sum(e.taxAmount), 0) from Expense e where e.status = "
            + "com.ntaganira.heritier.ibook.enums.ExpenseStatus.POSTED "
            + "and e.expenseDate >= :from and e.expenseDate <= :to")
    BigDecimal taxBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select e from Expense e where e.vendorId = :vendorId order by e.expenseDate desc")
    List<Expense> findByVendor(@Param("vendorId") Long vendorId);
}
