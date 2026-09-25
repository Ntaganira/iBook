/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : BankReconciliationRepository.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for bank reconciliations
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.BankReconciliation;
import com.ntaganira.heritier.ibook.enums.ReconciliationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BankReconciliationRepository extends JpaRepository<BankReconciliation, Long> {

    long countByStatus(ReconciliationStatus status);

    @Query("select r from BankReconciliation r where (:q is null or :q = '' "
            + "   or lower(r.reference) like lower(concat('%', :q, '%')) "
            + "   or lower(r.accountName) like lower(concat('%', :q, '%'))) "
            + "   and (:accountId is null or r.accountId = :accountId) "
            + "   and (:status is null or r.status = :status)")
    Page<BankReconciliation> search(@Param("q") String q,
                                    @Param("accountId") Long accountId,
                                    @Param("status") ReconciliationStatus status,
                                    Pageable pageable);

    @Query("select r from BankReconciliation r "
            + "where r.status = com.ntaganira.heritier.ibook.enums.ReconciliationStatus.COMPLETED "
            + "and r.accountId = :accountId order by r.statementDate desc, r.id desc")
    List<BankReconciliation> completedForAccount(@Param("accountId") Long accountId);

    @Query("select r from BankReconciliation r "
            + "where r.status = com.ntaganira.heritier.ibook.enums.ReconciliationStatus.COMPLETED "
            + "order by r.statementDate desc, r.id desc")
    List<BankReconciliation> allCompleted();
}
