/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : NumberingSequenceRepository.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for numbering sequences
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.NumberingSequence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NumberingSequenceRepository extends JpaRepository<NumberingSequence, Long> {

    List<NumberingSequence> findAllByOrderByNameAsc();

    Optional<NumberingSequence> findByDocType(String docType);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}