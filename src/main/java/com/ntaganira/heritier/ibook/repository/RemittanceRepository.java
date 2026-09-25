/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : RemittanceRepository.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for payroll remittances
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.Remittance;
import com.ntaganira.heritier.ibook.enums.RemittanceAuthority;
import com.ntaganira.heritier.ibook.enums.RemittanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface RemittanceRepository extends JpaRepository<Remittance, Long> {

    long countByStatus(RemittanceStatus status);

    @Query("select r from Remittance r where (:q is null or :q = '' "
            + "   or lower(r.reference) like lower(concat('%', :q, '%')) "
            + "   or lower(r.declarationNo) like lower(concat('%', :q, '%'))) "
            + "   and (:authority is null or r.authority = :authority) "
            + "   and (:status is null or r.status = :status)")
    Page<Remittance> search(@Param("q") String q,
                            @Param("authority") RemittanceAuthority authority,
                            @Param("status") RemittanceStatus status,
                            Pageable pageable);

    /**
     * What has already been handed over for a period. Paid remittances only — a draft is an
     * intention, and counting it would make an unpaid liability look settled.
     */
    @Query("select coalesce(sum(r.amount), 0) from Remittance r "
            + "where r.status = com.ntaganira.heritier.ibook.enums.RemittanceStatus.PAID "
            + "and r.authority = :authority "
            + "and r.periodStart <= :to and r.periodEnd >= :from")
    BigDecimal paidForPeriod(@Param("authority") RemittanceAuthority authority,
                             @Param("from") LocalDate from,
                             @Param("to") LocalDate to);

    List<Remittance> findTop10ByOrderByPaymentDateDescIdDesc();
}
