/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : InvoiceTemplateRepository.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for invoice templates
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.InvoiceTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface InvoiceTemplateRepository extends JpaRepository<InvoiceTemplate, Long> {

    List<InvoiceTemplate> findAllByOrderByNameAsc();

    @Query("select t from InvoiceTemplate t where t.defaultTemplate = true")
    Optional<InvoiceTemplate> findDefault();
}