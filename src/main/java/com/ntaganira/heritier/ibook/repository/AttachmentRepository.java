/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : AttachmentRepository.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for stored documents
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.Attachment;
import com.ntaganira.heritier.ibook.enums.DocumentCategory;
import com.ntaganira.heritier.ibook.enums.DocumentLinkType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    @Query("select a from Attachment a where (:q is null or :q = '' "
            + "   or lower(a.originalName) like lower(concat('%', :q, '%')) "
            + "   or lower(a.description) like lower(concat('%', :q, '%')) "
            + "   or lower(a.tags) like lower(concat('%', :q, '%')) "
            + "   or lower(a.linkLabel) like lower(concat('%', :q, '%'))) "
            + "   and (:category is null or a.category = :category) "
            + "   and (:linkType is null or a.linkType = :linkType) "
            + "   and a.template = :template "
            + "   and a.archived = :archived")
    Page<Attachment> search(@Param("q") String q,
                            @Param("category") DocumentCategory category,
                            @Param("linkType") DocumentLinkType linkType,
                            @Param("template") boolean template,
                            @Param("archived") boolean archived,
                            Pageable pageable);

    List<Attachment> findByLinkTypeAndLinkIdOrderByCreatedAtDesc(DocumentLinkType linkType, Long linkId);

    @Query("select a from Attachment a where a.archived = false and a.template = false "
            + "and a.linkType <> com.ntaganira.heritier.ibook.enums.DocumentLinkType.NONE "
            + "order by a.linkType asc, a.linkLabel asc, a.createdAt desc")
    List<Attachment> attached();

    @Query("select a from Attachment a where a.archived = false and a.template = false "
            + "and a.linkType = com.ntaganira.heritier.ibook.enums.DocumentLinkType.NONE "
            + "order by a.createdAt desc")
    List<Attachment> unattached();

    List<Attachment> findByChecksumAndArchivedFalse(String checksum);

    long countByArchivedFalseAndTemplateFalse();

    long countByArchivedTrue();

    long countByTemplateTrueAndArchivedFalse();

    @Query("select count(a) from Attachment a where a.archived = false and a.template = false "
            + "and a.linkType = com.ntaganira.heritier.ibook.enums.DocumentLinkType.NONE")
    long countUnattached();

    @Query("select coalesce(sum(a.sizeBytes), 0) from Attachment a where a.archived = false")
    Long totalBytes();
}
