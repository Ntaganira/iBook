/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : TaxFilingRepository.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for tax filings
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.TaxFiling;
import com.ntaganira.heritier.ibook.enums.FilingStatus;
import com.ntaganira.heritier.ibook.enums.TaxFilingType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TaxFilingRepository extends JpaRepository<TaxFiling, Long> {

    @Query("select f from TaxFiling f where (:type is null or f.filingType = :type) "
            + "and (:status is null or f.status = :status)")
    Page<TaxFiling> search(@Param("type") TaxFilingType type,
                           @Param("status") FilingStatus status,
                           Pageable pageable);

    /** Guards against filing the same period twice for the same tax. */
    Optional<TaxFiling> findByFilingTypeAndPeriodFromAndPeriodTo(TaxFilingType filingType,
                                                                 LocalDate periodFrom,
                                                                 LocalDate periodTo);

    long countByStatus(FilingStatus status);

    @Query("select coalesce(sum(f.declaredAmount - f.paidAmount), 0) from TaxFiling f "
            + "where f.status <> com.ntaganira.heritier.ibook.enums.FilingStatus.PAID")
    BigDecimal totalOutstanding();

    @Query("select f from TaxFiling f where f.status <> com.ntaganira.heritier.ibook.enums.FilingStatus.PAID "
            + "and f.dueDate < :today order by f.dueDate asc")
    List<TaxFiling> findOverdue(@Param("today") LocalDate today);
}
