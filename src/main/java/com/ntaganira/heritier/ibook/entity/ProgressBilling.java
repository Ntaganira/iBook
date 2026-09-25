/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : ProgressBilling.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : JPA entity for a claim against a share of an agreed fixed price
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.ProgressBillingMethod;
import com.ntaganira.heritier.ibook.enums.ProgressBillingStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "progress_billings")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProgressBilling implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "project_code", nullable = false)
    private String projectCode;

    @Column(name = "project_name", nullable = false)
    private String projectName;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    /** Counted per job, so a customer reads "claim 3 of the Kigali fit-out", not a global number. */
    @Column(name = "claim_number", nullable = false)
    @Builder.Default
    private int claimNumber = 1;

    @Column(name = "claim_date", nullable = false)
    private LocalDate claimDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false)
    @Builder.Default
    private ProgressBillingMethod method = ProgressBillingMethod.PERCENT_COMPLETE;

    /**
     * The agreed price as it stood when the claim was raised. Held on the claim rather than read
     * back off the job, because a contract variation agreed later must not silently restate what
     * an earlier claim said it was a percentage of.
     */
    @Column(name = "contract_value", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal contractValue = BigDecimal.ZERO;

    /** Cumulative percentage of the contract complete at this claim, on the percentage method. */
    @Column(name = "percent_complete", precision = 7, scale = 2)
    @Builder.Default
    private BigDecimal percentComplete = BigDecimal.ZERO;

    @Column(name = "previously_billed", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal previouslyBilled = BigDecimal.ZERO;

    @Column(name = "cumulative_billed", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal cumulativeBilled = BigDecimal.ZERO;

    /** This claim before retention: cumulative to date less everything already claimed. */
    @Column(name = "gross_amount", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal grossAmount = BigDecimal.ZERO;

    @Column(name = "retention_percent", precision = 7, scale = 2)
    @Builder.Default
    private BigDecimal retentionPercent = BigDecimal.ZERO;

    @Column(name = "retention_amount", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal retentionAmount = BigDecimal.ZERO;

    /** What actually goes on the invoice: the claim less whatever is being held back. */
    @Column(name = "net_amount", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal netAmount = BigDecimal.ZERO;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ProgressBillingStatus status = ProgressBillingStatus.DRAFT;

    @Column(name = "invoice_id")
    private Long invoiceId;

    @Column(name = "invoice_no")
    private String invoiceNo;

    @Column(name = "invoiced_at")
    private LocalDateTime invoicedAt;

    @Column(name = "cancelled_reason", length = 500)
    private String cancelledReason;

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
        return status == ProgressBillingStatus.DRAFT;
    }

    @Transient
    public boolean isInvoiced() {
        return status == ProgressBillingStatus.INVOICED;
    }

    @Transient
    public boolean isCancelled() {
        return status == ProgressBillingStatus.CANCELLED;
    }

    @Transient
    public boolean isEditable() {
        return status == ProgressBillingStatus.DRAFT;
    }

    @Transient
    public boolean isHoldingRetention() {
        return retentionAmount != null && retentionAmount.signum() > 0;
    }

    @Transient
    public String getClaimLabel() {
        return projectCode + " · " + claimNumber;
    }
}
