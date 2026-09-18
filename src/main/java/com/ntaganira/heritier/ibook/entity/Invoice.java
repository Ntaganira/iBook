/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : Invoice.java
 * - Date      : 2026. 09. 17.
 * - User      : Hntaganira
 * - Desc      : JPA entity for sales invoices
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
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoices")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_no", nullable = false, unique = true)
    private String invoiceNo;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "payment_terms")
    private String paymentTerms;

    @Column(name = "reference")
    private String reference;

    @Column(name = "currency_code", nullable = false)
    @Builder.Default
    private String currencyCode = "RWF";

    @Column(name = "exchange_rate", precision = 16, scale = 6)
    @Builder.Default
    private BigDecimal exchangeRate = BigDecimal.ONE;

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

    /** Settled by credit notes rather than by cash, so it stays out of payment reporting. */
    @Column(name = "credited_amount", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal creditedAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.DRAFT;

    @Column(name = "customer_message", length = 1000)
    private String customerMessage;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    @Column(name = "ebm_reference")
    private String ebmReference;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @OrderBy("sortOrder asc")
    private List<InvoiceLine> lines = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addLine(InvoiceLine line) {
        line.setInvoice(this);
        lines.add(line);
    }

    @Transient
    public BigDecimal getBalanceDue() {
        BigDecimal t = total == null ? BigDecimal.ZERO : total;
        return t.subtract(getSettledAmount());
    }

    @Transient
    public BigDecimal getSettledAmount() {
        BigDecimal p = amountPaid == null ? BigDecimal.ZERO : amountPaid;
        BigDecimal c = creditedAmount == null ? BigDecimal.ZERO : creditedAmount;
        return p.add(c);
    }

    /** Status implied by what has been settled, in cash or in credit notes. */
    @Transient
    public DocumentStatus getSettlementStatus() {
        BigDecimal settled = getSettledAmount();
        if (settled.signum() <= 0) {
            return DocumentStatus.OPEN;
        }
        BigDecimal t = total == null ? BigDecimal.ZERO : total;
        return settled.compareTo(t) >= 0 ? DocumentStatus.PAID : DocumentStatus.PARTIALLY_PAID;
    }

    @Transient
    public boolean isEditable() {
        return status == DocumentStatus.DRAFT;
    }

    @Transient
    public boolean isPosted() {
        return journalEntryId != null;
    }

    @Transient
    public DocumentStatus getDisplayStatus() {
        if (status == DocumentStatus.OPEN || status == DocumentStatus.PARTIALLY_PAID) {
            if (dueDate != null && dueDate.isBefore(LocalDate.now())) {
                return DocumentStatus.OVERDUE;
            }
        }
        return status;
    }

    @Transient
    public long getDaysOverdue() {
        if (dueDate == null || getBalanceDue().signum() <= 0) {
            return 0;
        }
        LocalDate today = LocalDate.now();
        return dueDate.isBefore(today) ? java.time.temporal.ChronoUnit.DAYS.between(dueDate, today) : 0;
    }
}
