/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : PayslipRepository.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for payslips
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.Payslip;
import com.ntaganira.heritier.ibook.enums.PayrollRunStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface PayslipRepository extends JpaRepository<Payslip, Long> {

    List<Payslip> findByRunIdOrderByEmployeeNameAsc(Long runId);

    /**
     * Payslips across every run, which is what the payslip list is for — somebody looking for one
     * person's payslip does not know or care which run it came out of.
     *
     * <p>The dates are required rather than optional. PostgreSQL cannot work out the type of a
     * bare date parameter compared to null, so an {@code is null} guard on one fails the whole
     * query — and the page always has a date range anyway, because it defaults to this year.
     */
    @Query("select p from Payslip p where (:q is null or :q = '' "
            + "   or lower(p.employeeName) like lower(concat('%', :q, '%')) "
            + "   or lower(p.employeeNo) like lower(concat('%', :q, '%')) "
            + "   or lower(p.rssbNumber) like lower(concat('%', :q, '%'))) "
            + "   and (:employeeId is null or p.employeeId = :employeeId) "
            + "   and (:status is null or p.run.status = :status) "
            + "   and p.payDate >= :from and p.payDate <= :to")
    Page<Payslip> search(@Param("q") String q,
                         @Param("employeeId") Long employeeId,
                         @Param("status") PayrollRunStatus status,
                         @Param("from") LocalDate from,
                         @Param("to") LocalDate to,
                         Pageable pageable);

    @Query("select p from Payslip p where p.employeeId = :employeeId "
            + "order by p.payDate desc, p.id desc")
    List<Payslip> findForEmployee(@Param("employeeId") Long employeeId);

    long countByEmployeeId(Long employeeId);

    /**
     * What each authority is owed for a period, read from posted runs only — a draft run has not
     * created a liability, so including it would invite somebody to pay over money the ledger does
     * not yet say is due.
     */
    @Query("select coalesce(sum(p.paye), 0) from Payslip p "
            + "where p.run.status = com.ntaganira.heritier.ibook.enums.PayrollRunStatus.POSTED "
            + "and p.payDate >= :from and p.payDate <= :to")
    BigDecimal payeBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select coalesce(sum(p.pensionEmployee + p.pensionEmployer + p.maternityEmployee "
            + "  + p.maternityEmployer + p.occupationalHazard + p.medicalEmployee "
            + "  + p.medicalEmployer), 0) from Payslip p "
            + "where p.run.status = com.ntaganira.heritier.ibook.enums.PayrollRunStatus.POSTED "
            + "and p.payDate >= :from and p.payDate <= :to")
    BigDecimal rssbBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select coalesce(sum(p.cbhi), 0) from Payslip p "
            + "where p.run.status = com.ntaganira.heritier.ibook.enums.PayrollRunStatus.POSTED "
            + "and p.payDate >= :from and p.payDate <= :to")
    BigDecimal cbhiBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select p from Payslip p "
            + "where p.run.status = com.ntaganira.heritier.ibook.enums.PayrollRunStatus.POSTED "
            + "and p.payDate >= :from and p.payDate <= :to "
            + "order by p.employeeName asc")
    List<Payslip> postedBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
