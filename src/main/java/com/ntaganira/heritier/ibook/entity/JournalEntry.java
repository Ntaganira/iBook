/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : JournalEntry.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : JPA entity for journal entries
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.JournalEntryStatus;
import com.ntaganira.heritier.ibook.enums.JournalEntryType;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "journal_entries")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entry_no", nullable = false, unique = true)
    private String entryNo;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    @Builder.Default
    private JournalEntryType type = JournalEntryType.MANUAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private JournalEntryStatus status = JournalEntryStatus.DRAFT;

    @Column(name = "reference")
    private String reference;

    @Column(name = "memo", length = 1000)
    private String memo;

    @Column(name = "total_debits", precision = 16, scale = 2)
    private BigDecimal totalDebits;

    @Column(name = "total_credits", precision = 16, scale = 2)
    private BigDecimal totalCredits;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @OrderBy("sortOrder asc")
    private List<JournalLine> lines = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addLine(JournalLine line) {
        line.setEntry(this);
        lines.add(line);
    }
}