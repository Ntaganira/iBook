/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : RecurringJournal.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : JPA entity for a journal entry that repeats on a cycle
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.RecurrenceFrequency;
import com.ntaganira.heritier.ibook.enums.RecurringJournalStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "recurring_journals")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "lines")
@ToString(exclude = "lines")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringJournal implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Named rather than numbered — it is a template, not a document. */
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false)
    @Builder.Default
    private RecurrenceFrequency frequency = RecurrenceFrequency.MONTHLY;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "max_occurrences")
    private Integer maxOccurrences;

    /** The date the next generated entry will carry. */
    @Column(name = "next_run_date")
    private LocalDate nextRunDate;

    /**
     * Posts each generated entry straight to the ledger with nobody in the loop. Off by default:
     * an entry that repeats unchanged every month is exactly the kind that goes on being wrong
     * for a year before anybody notices.
     */
    @Column(name = "auto_post", nullable = false)
    @Builder.Default
    private boolean autoPost = false;

    @Column(name = "reference")
    private String reference;

    @Column(name = "memo", length = 1000)
    private String memo;

    @Column(name = "total_debits", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal totalDebits = BigDecimal.ZERO;

    @Column(name = "total_credits", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal totalCredits = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private RecurringJournalStatus status = RecurringJournalStatus.DRAFT;

    @Column(name = "occurrences_generated", nullable = false)
    @Builder.Default
    private int occurrencesGenerated = 0;

    @Column(name = "last_run_date")
    private LocalDate lastRunDate;

    @Column(name = "last_entry_id")
    private Long lastEntryId;

    @Column(name = "last_entry_no")
    private String lastEntryNo;

    @Column(name = "stopped_reason", length = 500)
    private String stoppedReason;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "recurringJournal", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @OrderBy("sortOrder asc")
    private List<RecurringJournalLine> lines = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addLine(RecurringJournalLine line) {
        line.setRecurringJournal(this);
        lines.add(line);
    }

    @Transient
    public boolean isDraft() {
        return status == RecurringJournalStatus.DRAFT;
    }

    @Transient
    public boolean isRunning() {
        return status == RecurringJournalStatus.ACTIVE;
    }

    @Transient
    public boolean isPaused() {
        return status == RecurringJournalStatus.PAUSED;
    }

    @Transient
    public boolean isCancelled() {
        return status == RecurringJournalStatus.CANCELLED;
    }

    @Transient
    public boolean isStopped() {
        return status == RecurringJournalStatus.CANCELLED
                || status == RecurringJournalStatus.COMPLETED;
    }

    @Transient
    public boolean isEditable() {
        return status != RecurringJournalStatus.CANCELLED;
    }

    /**
     * Debits equal credits. Checked before a schedule may start <em>and</em> again as each entry
     * is written: a schedule that does not balance would otherwise post a broken entry every month
     * without anybody being asked.
     */
    @Transient
    public boolean isBalanced() {
        return zero(totalDebits).compareTo(zero(totalCredits)) == 0
                && zero(totalDebits).signum() > 0;
    }

    @Transient
    public BigDecimal getDifference() {
        return zero(totalDebits).subtract(zero(totalCredits));
    }

    @Transient
    public boolean isExhausted() {
        if (maxOccurrences != null && occurrencesGenerated >= maxOccurrences) {
            return true;
        }
        return endDate != null && nextRunDate != null && nextRunDate.isAfter(endDate);
    }

    @Transient
    public boolean isDue() {
        return status == RecurringJournalStatus.ACTIVE
                && nextRunDate != null
                && !nextRunDate.isAfter(LocalDate.now())
                && !isExhausted()
                && !lines.isEmpty();
    }

    @Transient
    public Integer getRemainingOccurrences() {
        return maxOccurrences == null ? null : Math.max(0, maxOccurrences - occurrencesGenerated);
    }

    @Transient
    public long getDaysToNextRun() {
        return nextRunDate == null ? 0 : ChronoUnit.DAYS.between(LocalDate.now(), nextRunDate);
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
