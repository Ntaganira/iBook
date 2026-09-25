/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : Project.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : JPA entity for a job that time and cost are booked against
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.ProjectBillingType;
import com.ntaganira.heritier.ibook.enums.ProjectStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "projects")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    /** Absent on an internal project, which is worth tracking cost on but is billed to nobody. */
    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ProjectStatus status = ProjectStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_type", nullable = false)
    @Builder.Default
    private ProjectBillingType billingType = ProjectBillingType.TIME_AND_MATERIALS;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "manager")
    private String manager;

    /** The agreed price on a fixed-price job. Hours are still booked, but they do not bill. */
    @Column(name = "fixed_price", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal fixedPrice = BigDecimal.ZERO;

    @Column(name = "default_bill_rate", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal defaultBillRate = BigDecimal.ZERO;

    /**
     * What an hour on this job costs the business. A management figure only — the wages behind it
     * reach the ledger through payroll, never through a timesheet.
     */
    @Column(name = "default_cost_rate", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal defaultCostRate = BigDecimal.ZERO;

    @Column(name = "estimated_hours", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal estimatedHours = BigDecimal.ZERO;

    @Column(name = "currency_code", length = 8)
    @Builder.Default
    private String currencyCode = "RWF";

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

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
        return status == ProjectStatus.DRAFT;
    }

    @Transient
    public boolean isRunning() {
        return status == ProjectStatus.ACTIVE;
    }

    @Transient
    public boolean isOnHold() {
        return status == ProjectStatus.ON_HOLD;
    }

    @Transient
    public boolean isCompleted() {
        return status == ProjectStatus.COMPLETED;
    }

    @Transient
    public boolean isCancelled() {
        return status == ProjectStatus.CANCELLED;
    }

    /**
     * Time can only be booked while the job is live. A completed or cancelled job that still
     * accepts hours would keep moving after somebody has reported on it.
     */
    @Transient
    public boolean isOpenForTime() {
        return status == ProjectStatus.DRAFT || status == ProjectStatus.ACTIVE
                || status == ProjectStatus.ON_HOLD;
    }

    @Transient
    public boolean isFixedPrice() {
        return billingType == ProjectBillingType.FIXED_PRICE;
    }

    @Transient
    public boolean isHourlyBillable() {
        return billingType.isHourlyBillable() && status != ProjectStatus.CANCELLED;
    }

    /** A job with nobody to bill cannot raise an invoice however its hours are marked. */
    @Transient
    public boolean isInvoiceable() {
        return customerId != null && isHourlyBillable();
    }

    @Transient
    public boolean isOverdue() {
        return endDate != null && endDate.isBefore(LocalDate.now())
                && (status == ProjectStatus.ACTIVE || status == ProjectStatus.ON_HOLD);
    }

    @Transient
    public String getDisplayName() {
        return code + " — " + name;
    }
}
