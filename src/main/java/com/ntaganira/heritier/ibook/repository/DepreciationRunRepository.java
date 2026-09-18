/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : DepreciationRunRepository.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for depreciation runs
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.DepreciationRun;
import com.ntaganira.heritier.ibook.enums.DepreciationRunStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DepreciationRunRepository extends JpaRepository<DepreciationRun, Long> {

    Optional<DepreciationRun> findByRunNo(String runNo);

    @Query("select r from DepreciationRun r where (:q is null or :q = '' "
            + "   or lower(r.runNo) like lower(concat('%', :q, '%')) "
            + "   or lower(r.notes) like lower(concat('%', :q, '%'))) "
            + "   and (:status is null or r.status = :status)")
    Page<DepreciationRun> search(@Param("q") String q,
                                 @Param("status") DepreciationRunStatus status,
                                 Pageable pageable);

    long countByStatus(DepreciationRunStatus status);

    @Query("select coalesce(sum(r.totalCharge), 0) from DepreciationRun r where r.status = "
            + "com.ntaganira.heritier.ibook.enums.DepreciationRunStatus.POSTED")
    BigDecimal totalPosted();

    @Query("select r from DepreciationRun r where r.status = "
            + "com.ntaganira.heritier.ibook.enums.DepreciationRunStatus.POSTED "
            + "order by r.periodEnd desc")
    List<DepreciationRun> findPostedNewestFirst();

    boolean existsByPeriodEndAndStatus(LocalDate periodEnd, DepreciationRunStatus status);
}
