/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : ProgressBillingRepository.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for progress claims
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.ProgressBilling;
import com.ntaganira.heritier.ibook.enums.ProgressBillingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface ProgressBillingRepository extends JpaRepository<ProgressBilling, Long> {

    List<ProgressBilling> findByProjectIdOrderByClaimNumberAsc(Long projectId);

    long countByStatus(ProgressBillingStatus status);

    long countByProjectIdAndStatus(Long projectId, ProgressBillingStatus status);

    @Query("select b from ProgressBilling b where (:q is null or :q = '' "
            + "   or lower(b.projectCode) like lower(concat('%', :q, '%')) "
            + "   or lower(b.projectName) like lower(concat('%', :q, '%')) "
            + "   or lower(b.customerName) like lower(concat('%', :q, '%')) "
            + "   or lower(b.invoiceNo) like lower(concat('%', :q, '%'))) "
            + "   and (:status is null or b.status = :status) "
            + "   and (:projectId is null or b.projectId = :projectId)")
    Page<ProgressBilling> search(@Param("q") String q,
                                 @Param("status") ProgressBillingStatus status,
                                 @Param("projectId") Long projectId,
                                 Pageable pageable);

    /** What a job has actually claimed: cancelled claims never counted, drafts not yet claimed. */
    @Query("select coalesce(sum(b.grossAmount), 0) from ProgressBilling b "
            + "where b.projectId = :projectId "
            + "and b.status = com.ntaganira.heritier.ibook.enums.ProgressBillingStatus.INVOICED")
    BigDecimal billedOn(@Param("projectId") Long projectId);

    @Query("select coalesce(sum(b.retentionAmount), 0) from ProgressBilling b "
            + "where b.projectId = :projectId "
            + "and b.status = com.ntaganira.heritier.ibook.enums.ProgressBillingStatus.INVOICED")
    BigDecimal retainedOn(@Param("projectId") Long projectId);

    @Query("select coalesce(max(b.claimNumber), 0) from ProgressBilling b where b.projectId = :projectId")
    int lastClaimNumber(@Param("projectId") Long projectId);
}
