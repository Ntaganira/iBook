/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : DocumentExtractionRepository.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for captured documents
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.DocumentExtraction;
import com.ntaganira.heritier.ibook.enums.ExtractionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DocumentExtractionRepository extends JpaRepository<DocumentExtraction, Long> {

    Optional<DocumentExtraction> findByAttachmentId(Long attachmentId);

    boolean existsByAttachmentId(Long attachmentId);

    long countByStatus(ExtractionStatus status);

    @Query("select e from DocumentExtraction e where (:q is null or :q = '' "
            + "   or lower(e.supplierName) like lower(concat('%', :q, '%')) "
            + "   or lower(e.documentNo) like lower(concat('%', :q, '%')) "
            + "   or lower(e.fileName) like lower(concat('%', :q, '%'))) "
            + "   and (:status is null or e.status = :status)")
    Page<DocumentExtraction> search(@Param("q") String q,
                                    @Param("status") ExtractionStatus status,
                                    Pageable pageable);
}
