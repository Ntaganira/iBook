/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : PayrollSettingsRepository.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for payroll settings
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.PayrollSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PayrollSettingsRepository extends JpaRepository<PayrollSettings, Long> {

    /** There is one set of rates for the company; this is how the service gets hold of it. */
    Optional<PayrollSettings> findFirstByOrderByIdAsc();
}
