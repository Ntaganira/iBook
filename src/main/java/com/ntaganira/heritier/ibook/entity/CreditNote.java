/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : CreditNote.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : JPA entity for customer credit notes
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.CreditNoteStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "credit_notes")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "lines")
@ToString(exclude = "lines")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditNote implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "credit_note_no", nullable = false, unique = true)
    private String creditNoteNo;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "credit_date", nullable = false)
    private LocalDate creditDate;

    @Column(name = "reference")
    private String reference;

    @Column(name = "reason", length = 500)
    private String reason;

    /** Optional link to the invoice being credited; null for a standalone customer credit. */
    @Column(name = "invoice_id")
    private Long invoiceId;

    @Column(name = "invoice_no")
    private String invoiceNo;

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

    /** How much of this note has been set against the linked invoice. */
    @Column(name = "applied_amount", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal appliedAmount = BigDecimal.ZERO;

    /** Goods coming back into stock, which also reverses the cost of sale. */
    @Column(name = "restock_items", nullable = false)
    @Builder.Default
    private boolean restockItems = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private CreditNoteStatus status = CreditNoteStatus.DRAFT;

    @Column(name = "customer_message", length = 1000)
    private String customerMessage;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "creditNote", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @OrderBy("sortOrder asc")
    private List<CreditNoteLine> lines = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addLine(CreditNoteLine line) {
        line.setCreditNote(this);
        lines.add(line);
    }

    @Transient
    public boolean isEditable() {
        return status == CreditNoteStatus.DRAFT;
    }

    @Transient
    public boolean isPosted() {
        return journalEntryId != null;
    }

    @Transient
    public boolean isLinked() {
        return invoiceId != null;
    }

    /** Credit still available to set against future invoices. */
    @Transient
    public BigDecimal getUnappliedAmount() {
        BigDecimal t = total == null ? BigDecimal.ZERO : total;
        BigDecimal a = appliedAmount == null ? BigDecimal.ZERO : appliedAmount;
        return t.subtract(a);
    }
}
