/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : PayrollRunRepository.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for payroll runs
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.PayrollRun;
import com.ntaganira.heritier.ibook.enums.PayrollRunStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, Long> {

    long countByStatus(PayrollRunStatus status);

    @Query("select r from PayrollRun r where (:q is null or :q = '' "
            + "   or lower(r.runNo) like lower(concat('%', :q, '%')) "
            + "   or lower(r.name) like lower(concat('%', :q, '%'))) "
            + "   and (:status is null or r.status = :status)")
    Page<PayrollRun> search(@Param("q") String q,
                            @Param("status") PayrollRunStatus status,
                            Pageable pageable);

    /**
     * Runs whose period overlaps the one asked about. Used to refuse a second run over the same
     * weeks, which would pay everybody twice and be invisible on any total.
     */
    @Query("select r from PayrollRun r "
            + "where r.status <> com.ntaganira.heritier.ibook.enums.PayrollRunStatus.VOID "
            + "and r.periodStart <= :to and r.periodEnd >= :from "
            + "and (:excludeId is null or r.id <> :excludeId)")
    List<PayrollRun> overlapping(@Param("from") LocalDate from,
                                 @Param("to") LocalDate to,
                                 @Param("excludeId") Long excludeId);

    @Query("select r from PayrollRun r "
            + "where r.status = com.ntaganira.heritier.ibook.enums.PayrollRunStatus.POSTED "
            + "and r.payDate >= :from and r.payDate <= :to "
            + "order by r.payDate asc, r.id asc")
    List<PayrollRun> postedPaidBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select coalesce(sum(r.totalNetPay), 0) from PayrollRun r "
            + "where r.status = com.ntaganira.heritier.ibook.enums.PayrollRunStatus.POSTED "
            + "and r.payDate >= :from and r.payDate <= :to")
    BigDecimal netPaidBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    List<PayrollRun> findTop5ByOrderByPayDateDescIdDesc();
}
