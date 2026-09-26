/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : IntegrationRepository.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for integration settings
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.Integration;
import com.ntaganira.heritier.ibook.enums.IntegrationProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IntegrationRepository extends JpaRepository<Integration, Long> {

    List<Integration> findByProviderOrderByLabelAsc(IntegrationProvider provider);

    Optional<Integration> findFirstByProviderOrderByIdAsc(IntegrationProvider provider);

    boolean existsByProviderAndLabelIgnoreCase(IntegrationProvider provider, String label);

    boolean existsByProviderAndLabelIgnoreCaseAndIdNot(IntegrationProvider provider, String label, Long id);

    long countByEnabledTrue();
}
