/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : BankStatementLineRepository.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for bank statement lines
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.BankStatementLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BankStatementLineRepository extends JpaRepository<BankStatementLine, Long> {

    List<BankStatementLine> findByReconciliationIdOrderByLineDateAscIdAsc(Long reconciliationId);

    /**
     * Ledger lines already accounted for by a completed reconciliation. A line matched once has
     * been agreed with the bank; offering it again on a later statement would let the same
     * movement be reconciled twice and hide a genuine difference.
     */
    @Query("select l.matchedLineId from BankStatementLine l "
            + "where l.matchedLineId is not null "
            + "and l.reconciliation.status = com.ntaganira.heritier.ibook.enums.ReconciliationStatus.COMPLETED "
            + "and l.reconciliation.accountId = :accountId "
            + "and (:excludeId is null or l.reconciliation.id <> :excludeId)")
    List<Long> reconciledLineIds(@Param("accountId") Long accountId,
                                 @Param("excludeId") Long excludeId);
}
