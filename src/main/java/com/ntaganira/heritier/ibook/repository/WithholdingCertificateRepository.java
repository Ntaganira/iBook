/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : WithholdingCertificateRepository.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for withholding certificates
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.WithholdingCertificate;
import com.ntaganira.heritier.ibook.enums.WithholdingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface WithholdingCertificateRepository extends JpaRepository<WithholdingCertificate, Long> {

    long countByStatus(WithholdingStatus status);

    @Query("select c from WithholdingCertificate c where (:q is null or :q = '' "
            + "   or lower(c.vendorName) like lower(concat('%', :q, '%')) "
            + "   or lower(c.certificateNo) like lower(concat('%', :q, '%')) "
            + "   or lower(c.billNo) like lower(concat('%', :q, '%'))) "
            + "   and (:vendorId is null or c.vendorId = :vendorId) "
            + "   and (:status is null or c.status = :status)")
    Page<WithholdingCertificate> search(@Param("q") String q,
                                        @Param("vendorId") Long vendorId,
                                        @Param("status") WithholdingStatus status,
                                        Pageable pageable);

    /**
     * Certificates already raised against a bill. Used to refuse withholding the same bill twice,
     * which would take money off the supplier once and owe it to the RRA twice.
     */
    @Query("select c from WithholdingCertificate c where c.billId = :billId "
            + "and c.status <> com.ntaganira.heritier.ibook.enums.WithholdingStatus.VOID")
    List<WithholdingCertificate> liveForBill(@Param("billId") Long billId);

    @Query("select coalesce(sum(c.amount), 0) from WithholdingCertificate c "
            + "where c.status = com.ntaganira.heritier.ibook.enums.WithholdingStatus.ISSUED "
            + "and c.certificateDate >= :from and c.certificateDate <= :to")
    BigDecimal withheldBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select c from WithholdingCertificate c "
            + "where c.status = com.ntaganira.heritier.ibook.enums.WithholdingStatus.ISSUED "
            + "and c.certificateDate >= :from and c.certificateDate <= :to "
            + "order by c.vendorName asc, c.certificateDate asc")
    List<WithholdingCertificate> issuedBetween(@Param("from") LocalDate from,
                                               @Param("to") LocalDate to);
}
