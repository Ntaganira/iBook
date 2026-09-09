/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : NumberingSequence.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : JPA entity for document numbering sequences
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "numbering_sequences")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NumberingSequence implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "doc_type", nullable = false)
    private String docType;

    @Column(name = "prefix")
    private String prefix;

    @Column(name = "suffix")
    private String suffix;

    @Column(name = "padding", nullable = false)
    @Builder.Default
    private int padding = 4;

    @Column(name = "next_number", nullable = false)
    @Builder.Default
    private long nextNumber = 1;

    @Column(name = "reset_yearly", nullable = false)
    @Builder.Default
    private boolean resetYearly = false;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public String previewNext() {
        String padded = Long.toString(nextNumber);
        StringBuilder sb = new StringBuilder();
        for (int i = padded.length(); i < Math.max(1, padding); i++) {
            sb.append('0');
        }
        sb.append(padded);
        return (prefix == null ? "" : prefix) + sb + (suffix == null ? "" : suffix);
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}