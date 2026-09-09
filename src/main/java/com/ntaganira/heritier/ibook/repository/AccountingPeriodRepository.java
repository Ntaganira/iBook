/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : AccountingPeriodRepository.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for accounting periods
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.AccountingPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, Long> {

    List<AccountingPeriod> findAllByOrderByStartDateDesc();

    List<AccountingPeriod> findByFiscalYearOrderByStartDateAsc(int fiscalYear);

    Optional<AccountingPeriod> findByCodeIgnoreCase(String code);

    boolean existsByFiscalYear(int fiscalYear);

    long countByFiscalYear(int fiscalYear);

    @Query("select count(p) from AccountingPeriod p where p.status = com.ntaganira.heritier.ibook.enums.PeriodStatus.OPEN")
    long countOpen();

    @Query("select count(p) from AccountingPeriod p where p.status = com.ntaganira.heritier.ibook.enums.PeriodStatus.CLOSED")
    long countClosed();
}