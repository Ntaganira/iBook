/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : AuditLogRepository.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for audit logs
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("select a from AuditLog a " +
            "where (:q is null or :q = '' or lower(a.actor) like lower(concat('%', :q, '%')) " +
            "   or lower(a.module) like lower(concat('%', :q, '%')) " +
            "   or lower(a.action) like lower(concat('%', :q, '%')) " +
            "   or lower(a.target) like lower(concat('%', :q, '%')))")
    Page<AuditLog> search(@Param("q") String q, Pageable pageable);
}