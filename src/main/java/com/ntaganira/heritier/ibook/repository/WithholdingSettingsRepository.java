/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : WithholdingSettingsRepository.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for withholding settings
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.WithholdingSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WithholdingSettingsRepository extends JpaRepository<WithholdingSettings, Long> {

    /** There is one withholding table for the company; this is how the service gets hold of it. */
    Optional<WithholdingSettings> findFirstByOrderByIdAsc();
}
