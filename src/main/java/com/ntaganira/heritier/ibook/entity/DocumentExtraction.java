/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : DocumentExtraction.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : What a captured document says, on its way to becoming an expense
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.ExtractionSource;
import com.ntaganira.heritier.ibook.enums.ExtractionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A scanned receipt or invoice on its way to becoming a transaction.
 *
 * <p>This is the half of document capture that decides money: what the paper says, checked, and
 * turned into a draft. Whether the figures were read by a machine or typed by a person changes
 * nothing about what happens next — which is the point. {@code source} records which it was,
 * because a typed figure was looked at by somebody and a machine-read one was not, and that is
 * worth knowing later.
 *
 * <p>Converting <strong>always</strong> produces a draft and never posts. A figure lifted off a
 * photograph is a claim about a document, not a fact about the books, and it belongs in front of
 * somebody before it reaches the ledger.
 */
@Entity
@Table(name = "document_extractions")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentExtraction implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The stored file this was read off. One capture per document, deliberately. */
    @Column(name = "attachment_id", nullable = false, unique = true)
    private Long attachmentId;

    @Column(name = "file_name")
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ExtractionStatus status = ExtractionStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    @Builder.Default
    private ExtractionSource source = ExtractionSource.TYPED;

    /** What the engine reported, where one ran. Null when a person typed the figures. */
    @Column(name = "engine_name")
    private String engineName;

    @Column(name = "supplier_name")
    private String supplierName;

    @Column(name = "vendor_id")
    private Long vendorId;

    @Column(name = "document_no")
    private String documentNo;

    @Column(name = "document_date")
    private LocalDate documentDate;

    @Column(name = "currency_code", length = 3)
    @Builder.Default
    private String currencyCode = "RWF";

    @Column(name = "subtotal", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tax_amount", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    /**
     * What the document says the total is, as printed. Held separately from subtotal and tax
     * rather than derived, because the point of the arithmetic check below is to compare the two.
     */
    @Column(name = "total", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "expense_account_id")
    private Long expenseAccountId;

    @Column(name = "payment_account_id")
    private Long paymentAccountId;

    @Column(name = "notes", length = 1000)
    private String notes;

    /** The draft this became, so the same paper cannot be turned into a second expense. */
    @Column(name = "expense_id")
    private Long expenseId;

    @Column(name = "expense_no")
    private String expenseNo;

    @Column(name = "captured_by")
    private String capturedBy;

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
    public boolean isPending() {
        return status == ExtractionStatus.PENDING;
    }

    @Transient
    public boolean isReady() {
        return status == ExtractionStatus.READY;
    }

    @Transient
    public boolean isConverted() {
        return status == ExtractionStatus.CONVERTED;
    }

    @Transient
    public boolean isEditable() {
        return status == ExtractionStatus.PENDING || status == ExtractionStatus.READY;
    }

    /**
     * Whether the parts add up to the printed total. A receipt whose subtotal and tax do not reach
     * its total has been misread somewhere, and converting it would carry the error into the books
     * — so this is shown rather than quietly corrected, because which of the three figures is
     * wrong is not something a machine can tell.
     */
    @Transient
    public boolean isArithmeticSound() {
        return zero(subtotal).add(zero(taxAmount)).compareTo(zero(total)) == 0;
    }

    @Transient
    public BigDecimal getArithmeticDifference() {
        return zero(total).subtract(zero(subtotal).add(zero(taxAmount)));
    }

    /** What a draft needs before it can be raised at all. */
    @Transient
    public boolean isConvertible() {
        return isEditable()
                && supplierName != null && !supplierName.isBlank()
                && documentDate != null
                && expenseAccountId != null
                && paymentAccountId != null
                && zero(total).signum() > 0;
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
