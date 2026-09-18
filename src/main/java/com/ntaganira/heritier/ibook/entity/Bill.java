/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : Bill.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : JPA entity for vendor bills (accounts payable)
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.DocumentStatus;
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
@Table(name = "bills")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "lines")
@ToString(exclude = "lines")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bill implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_no", nullable = false, unique = true)
    private String billNo;

    @Column(name = "vendor_id", nullable = false)
    private Long vendorId;

    @Column(name = "vendor_name", nullable = false)
    private String vendorName;

    @Column(name = "vendor_email")
    private String vendorEmail;

    @Column(name = "vendor_invoice_no")
    private String vendorInvoiceNo;

    @Column(name = "bill_date", nullable = false)
    private LocalDate billDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "payment_terms")
    private String paymentTerms;

    @Column(name = "reference")
    private String reference;

    @Column(name = "memo", length = 1000)
    private String memo;

    @Column(name = "notes", length = 1000)
    private String notes;

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

    @Column(name = "amount_paid", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.DRAFT;

    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @OrderBy("sortOrder asc")
    private List<BillLine> lines = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addLine(BillLine line) {
        line.setBill(this);
        lines.add(line);
    }

    public BigDecimal getBalanceDue() {
        if (status == DocumentStatus.VOID) {
            return BigDecimal.ZERO;
        }
        BigDecimal gross = total == null ? BigDecimal.ZERO : total;
        BigDecimal paid = amountPaid == null ? BigDecimal.ZERO : amountPaid;
        BigDecimal due = gross.subtract(paid);
        return due.signum() < 0 ? BigDecimal.ZERO : due;
    }

    public boolean isPosted() {
        return journalEntryId != null;
    }

    public boolean isEditable() {
        return status == DocumentStatus.DRAFT;
    }

    public DocumentStatus getDisplayStatus() {
        if (status == DocumentStatus.VOID || status == DocumentStatus.DRAFT) {
            return status;
        }
        if (getBalanceDue().signum() == 0) {
            return DocumentStatus.PAID;
        }
        if (dueDate != null && dueDate.isBefore(LocalDate.now())) {
            return DocumentStatus.OVERDUE;
        }
        BigDecimal paid = amountPaid == null ? BigDecimal.ZERO : amountPaid;
        return paid.signum() > 0 ? DocumentStatus.PARTIALLY_PAID : DocumentStatus.OPEN;
    }

    public long getDaysOverdue() {
        if (status == DocumentStatus.VOID || status == DocumentStatus.DRAFT
                || dueDate == null || getBalanceDue().signum() == 0) {
            return 0L;
        }
        long days = ChronoUnit.DAYS.between(dueDate, LocalDate.now());
        return days > 0 ? days : 0L;
    }
}
