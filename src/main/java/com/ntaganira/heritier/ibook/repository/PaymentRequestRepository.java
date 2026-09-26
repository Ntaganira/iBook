/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : PaymentRequestRepository.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for payment requests
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.PaymentRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, Long> {

    /**
     * The dates are required rather than optional, for the same reason as elsewhere: PostgreSQL cannot
     * work out the type of a bare date parameter compared to null, so an {@code is null} guard on one
     * fails the whole query. The page always has a range anyway.
     */
    List<PaymentRequest> findByRequestedOnBetweenOrderByRequestedOnDescIdDesc(LocalDate from, LocalDate to);

    List<PaymentRequest> findByInvoiceIdOrderByRequestedOnDescIdDesc(Long invoiceId);

    long countByInvoiceIdAndCancelledFalse(Long invoiceId);
}
