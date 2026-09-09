/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : InvoiceTemplate.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : JPA entity for invoice templates
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "invoice_templates")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceTemplate implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "layout", nullable = false)
    @Builder.Default
    private String layout = "modern";

    @Column(name = "accent_color")
    @Builder.Default
    private String accentColor = "#166534";

    @Column(name = "paper_size", nullable = false)
    @Builder.Default
    private String paperSize = "A4";

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean defaultTemplate = false;

    @Column(name = "shows_logo", nullable = false)
    @Builder.Default
    private boolean showsLogo = true;

    @Column(name = "shows_tax_summary", nullable = false)
    @Builder.Default
    private boolean showsTaxSummary = true;

    @Column(name = "includes_terms", nullable = false)
    @Builder.Default
    private boolean includesTerms = true;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}