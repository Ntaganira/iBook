/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : RecurringInvoice.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : JPA entity for a recurring invoice schedule
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.RecurrenceFrequency;
import com.ntaganira.heritier.ibook.enums.RecurringInvoiceStatus;
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
@Table(name = "recurring_invoices")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "lines")
@ToString(exclude = "lines")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringInvoice implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** A schedule is named rather than numbered — it is a template, not a document. */
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_email")
    private String customerEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false)
    @Builder.Default
    private RecurrenceFrequency frequency = RecurrenceFrequency.MONTHLY;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Optional hard stop; the schedule completes once this date is passed. */
    @Column(name = "end_date")
    private LocalDate endDate;

    /** Optional cap on how many invoices the schedule may raise in total. */
    @Column(name = "max_occurrences")
    private Integer maxOccurrences;

    /** The issue date the next generated invoice will carry. */
    @Column(name = "next_run_date")
    private LocalDate nextRunDate;

    @Column(name = "due_days", nullable = false)
    @Builder.Default
    private int dueDays = 30;

    /** Posts each generated invoice to the ledger without a human review step. */
    @Column(name = "auto_post", nullable = false)
    @Builder.Default
    private boolean autoPost = false;

    @Column(name = "reference")
    private String reference;

    @Column(name = "currency_code", nullable = false)
    @Builder.Default
    private String currencyCode = "RWF";

    @Column(name = "subtotal", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_amount", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private RecurringInvoiceStatus status = RecurringInvoiceStatus.DRAFT;

    @Column(name = "occurrences_generated", nullable = false)
    @Builder.Default
    private int occurrencesGenerated = 0;

    @Column(name = "last_run_date")
    private LocalDate lastRunDate;

    @Column(name = "last_invoice_id")
    private Long lastInvoiceId;

    @Column(name = "last_invoice_no")
    private String lastInvoiceNo;

    @Column(name = "customer_message", length = 1000)
    private String customerMessage;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "stopped_reason", length = 500)
    private String stoppedReason;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "recurringInvoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @OrderBy("sortOrder asc")
    private List<RecurringInvoiceLine> lines = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addLine(RecurringInvoiceLine line) {
        line.setRecurringInvoice(this);
        lines.add(line);
    }

    @Transient
    public boolean isEditable() {
        return status != RecurringInvoiceStatus.CANCELLED;
    }

    @Transient
    public boolean isRunning() {
        return status == RecurringInvoiceStatus.ACTIVE;
    }

    @Transient
    public boolean isStopped() {
        return status == RecurringInvoiceStatus.CANCELLED
                || status == RecurringInvoiceStatus.COMPLETED;
    }

    /** True once the schedule has run out of either time or occurrences. */
    @Transient
    public boolean isExhausted() {
        if (maxOccurrences != null && occurrencesGenerated >= maxOccurrences) {
            return true;
        }
        return endDate != null && nextRunDate != null && nextRunDate.isAfter(endDate);
    }

    @Transient
    public boolean isDue() {
        return status == RecurringInvoiceStatus.ACTIVE
                && nextRunDate != null
                && !nextRunDate.isAfter(LocalDate.now())
                && !isExhausted()
                && !lines.isEmpty();
    }

    @Transient
    public Integer getRemainingOccurrences() {
        if (maxOccurrences == null) {
            return null;
        }
        return Math.max(0, maxOccurrences - occurrencesGenerated);
    }

    @Transient
    public long getDaysToNextRun() {
        if (nextRunDate == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(LocalDate.now(), nextRunDate);
    }
}
