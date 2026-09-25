/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : TimeEntry.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : One booking of hours against a project
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.TimeEntryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "time_entries")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "project_code")
    private String projectCode;

    @Column(name = "project_name")
    private String projectName;

    /** Copied from the project so billable time can be grouped per customer without re-reading it. */
    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "customer_name")
    private String customerName;

    /**
     * Free text until there is a payroll register to pick from. Whoever did the work is the one
     * fact a timesheet cannot do without, so it is required rather than optional.
     */
    @Column(name = "person", nullable = false)
    private String person;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "hours", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal hours = BigDecimal.ZERO;

    @Column(name = "task")
    private String task;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "billable", nullable = false)
    @Builder.Default
    private boolean billable = true;

    @Column(name = "bill_rate", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal billRate = BigDecimal.ZERO;

    @Column(name = "billable_amount", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal billableAmount = BigDecimal.ZERO;

    @Column(name = "cost_rate", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal costRate = BigDecimal.ZERO;

    /**
     * Hours times the cost rate. Never posted: the wages behind these hours already reach the
     * ledger through payroll, so charging them again from a timesheet would count them twice.
     */
    @Column(name = "cost_amount", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal costAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private TimeEntryStatus status = TimeEntryStatus.DRAFT;

    @Column(name = "invoice_id")
    private Long invoiceId;

    @Column(name = "invoice_no")
    private String invoiceNo;

    @Column(name = "invoiced_at")
    private LocalDateTime invoicedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "rejected_reason", length = 500)
    private String rejectedReason;

    @Column(name = "created_by")
    private String createdBy;

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

    @Transient
    public boolean isDraft() {
        return status == TimeEntryStatus.DRAFT;
    }

    @Transient
    public boolean isApproved() {
        return status == TimeEntryStatus.APPROVED;
    }

    @Transient
    public boolean isRejected() {
        return status == TimeEntryStatus.REJECTED;
    }

    @Transient
    public boolean isInvoiced() {
        return status == TimeEntryStatus.INVOICED;
    }

    /**
     * Invoiced time is evidence for a document the customer has been sent, so it stops being a
     * working record and cannot be edited or deleted.
     */
    @Transient
    public boolean isEditable() {
        return status != TimeEntryStatus.INVOICED;
    }

    @Transient
    public boolean isReadyToBill() {
        return status == TimeEntryStatus.APPROVED && billable && customerId != null
                && billableAmount != null && billableAmount.signum() > 0;
    }

    public void recalculate() {
        BigDecimal h = hours == null ? BigDecimal.ZERO : hours;
        billableAmount = billable
                ? h.multiply(billRate == null ? BigDecimal.ZERO : billRate)
                        .setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        costAmount = h.multiply(costRate == null ? BigDecimal.ZERO : costRate)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
