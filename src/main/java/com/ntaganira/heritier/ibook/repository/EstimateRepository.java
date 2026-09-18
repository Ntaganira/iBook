/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : EstimateRepository.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for estimates
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.Estimate;
import com.ntaganira.heritier.ibook.enums.EstimateStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface EstimateRepository extends JpaRepository<Estimate, Long> {

    @Query("select e from Estimate e where (:q is null or :q = '' "
            + "   or lower(e.estimateNo) like lower(concat('%', :q, '%')) "
            + "   or lower(e.customerName) like lower(concat('%', :q, '%')) "
            + "   or lower(e.reference) like lower(concat('%', :q, '%'))) "
            + "   and (:status is null or e.status = :status) "
            + "   and (:customerId is null or e.customerId = :customerId)")
    Page<Estimate> search(@Param("q") String q,
                          @Param("status") EstimateStatus status,
                          @Param("customerId") Long customerId,
                          Pageable pageable);

    long countByStatus(EstimateStatus status);

    @Query("select coalesce(sum(e.total), 0) from Estimate e where e.status in :statuses")
    BigDecimal totalFor(@Param("statuses") List<EstimateStatus> statuses);

    @Query("select count(e) from Estimate e where e.status = "
            + "com.ntaganira.heritier.ibook.enums.EstimateStatus.SENT and e.expiryDate < :today")
    long countExpired(@Param("today") LocalDate today);

    List<Estimate> findByCustomerIdOrderByEstimateDateDesc(Long customerId);
}
