/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : TaxFiling.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : JPA entity for a recorded tax declaration
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.FilingStatus;
import com.ntaganira.heritier.ibook.enums.TaxFilingType;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "tax_filings")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxFiling implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "filing_type", nullable = false)
    @Builder.Default
    private TaxFilingType filingType = TaxFilingType.VAT;

    @Column(name = "period_from", nullable = false)
    private LocalDate periodFrom;

    @Column(name = "period_to", nullable = false)
    private LocalDate periodTo;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "declared_amount", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal declaredAmount = BigDecimal.ZERO;

    @Column(name = "paid_amount", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private FilingStatus status = FilingStatus.DRAFT;

    @Column(name = "reference")
    private String reference;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "submitted_at")
    private LocalDate submittedAt;

    @Column(name = "paid_at")
    private LocalDate paidAt;

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

    public BigDecimal getBalanceDue() {
        BigDecimal declared = declaredAmount == null ? BigDecimal.ZERO : declaredAmount;
        BigDecimal paid = paidAmount == null ? BigDecimal.ZERO : paidAmount;
        BigDecimal due = declared.subtract(paid);
        return due.signum() < 0 ? BigDecimal.ZERO : due;
    }

    public boolean isEditable() {
        return status == FilingStatus.DRAFT;
    }

    /** Overdue only matters once a return is due and still unpaid. */
    public long getDaysOverdue() {
        if (status == FilingStatus.PAID || dueDate == null) {
            return 0L;
        }
        long days = ChronoUnit.DAYS.between(dueDate, LocalDate.now());
        return days > 0 ? days : 0L;
    }

    public String getPeriodLabel() {
        return periodFrom + " \u2014 " + periodTo;
    }
}
