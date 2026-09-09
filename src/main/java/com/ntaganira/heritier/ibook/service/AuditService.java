/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : AuditService.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : Audit trail logging service
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.entity.AuditLog;
import com.ntaganira.heritier.ibook.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        return "system";
    }

    @Transactional
    public void log(String module, String action, String target, String detail) {
        auditLogRepository.save(AuditLog.builder()
                .actor(currentUsername())
                .module(module)
                .action(action)
                .target(target)
                .detail(detail)
                .build());
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> list(String q, Pageable pageable) {
        return (q == null || q.isBlank())
                ? auditLogRepository.findAll(pageable)
                : auditLogRepository.search(q.trim(), pageable);
    }
}