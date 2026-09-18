/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : DepreciationRun.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : JPA entity for a depreciation charge across the register
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.DepreciationRunStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "depreciation_runs")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "entries")
@ToString(exclude = "entries")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepreciationRun implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_no", nullable = false, unique = true)
    private String runNo;

    /**
     * Depreciation is charged up to this date. A run always brings every asset up to date rather
     * than charging one period, so there is no period start to record.
     */
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private DepreciationRunStatus status = DepreciationRunStatus.DRAFT;

    @Column(name = "asset_count", nullable = false)
    @Builder.Default
    private int assetCount = 0;

    @Column(name = "total_charge", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal totalCharge = BigDecimal.ZERO;

    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    @Column(name = "reversal_journal_entry_id")
    private Long reversalJournalEntryId;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    @Column(name = "stopped_reason", length = 500)
    private String stoppedReason;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @OrderBy("sortOrder asc")
    private List<DepreciationEntry> entries = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addEntry(DepreciationEntry entry) {
        entry.setRun(this);
        entries.add(entry);
    }

    @Transient
    public boolean isDraft() {
        return status == DepreciationRunStatus.DRAFT;
    }

    @Transient
    public boolean isPosted() {
        return status == DepreciationRunStatus.POSTED;
    }

    @Transient
    public boolean isVoided() {
        return status == DepreciationRunStatus.VOID;
    }

    @Transient
    public boolean isPostable() {
        return status == DepreciationRunStatus.DRAFT && !entries.isEmpty();
    }

    @Transient
    public int getChargedCount() {
        int n = 0;
        for (DepreciationEntry e : entries) {
            if (e.isCharged()) {
                n++;
            }
        }
        return n;
    }
}
