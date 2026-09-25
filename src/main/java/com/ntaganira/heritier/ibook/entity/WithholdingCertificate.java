/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : WithholdingCertificate.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : Tax withheld from one supplier bill, and the certificate proving it
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.WithholdingStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tax withheld from a supplier bill.
 *
 * <p>Withholding is not a cost. The full amount of the bill is still the company's expense; part of
 * it is simply paid to the RRA instead of to the supplier. So issuing a certificate posts
 * <strong>Dr accounts payable / Cr withholding payable</strong> — it moves the debt from the
 * supplier to the state and touches no expense account.
 *
 * <p>It is recorded through the ordinary bill-payment mechanism rather than as a separate kind of
 * event, because that is what it is: a part-settlement of the bill. That keeps the bill's own
 * balance, the aging and the payables report all saying the same thing, instead of leaving them to
 * disagree with the ledger by the amount withheld.
 *
 * <p>The certificate is what the supplier is entitled to receive, which is why the base, the rate
 * and the supplier's tax number are copied on rather than read back — the supplier may be edited
 * later, and the certificate has to go on saying what it said.
 *
 * <p>Deliberately limited to <strong>bills</strong>. A direct expense is already paid in full from
 * the bank, so there is nothing left to withhold from; withholding on that kind of spend belongs on
 * the document at the time it is entered, and pretending otherwise would credit money back into a
 * bank account that had already sent it.
 */
@Entity
@Table(name = "withholding_certificates")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WithholdingCertificate implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "certificate_no", nullable = false, unique = true)
    private String certificateNo;

    @Column(name = "bill_id", nullable = false)
    private Long billId;

    @Column(name = "bill_no")
    private String billNo;

    @Column(name = "vendor_id")
    private Long vendorId;

    @Column(name = "vendor_name")
    private String vendorName;

    /** The supplier's tax number as it stood when the certificate was issued. */
    @Column(name = "vendor_tax_id")
    private String vendorTaxId;

    @Column(name = "certificate_date", nullable = false)
    private LocalDate certificateDate;

    @Column(name = "rate_code")
    private String rateCode;

    @Column(name = "rate_name")
    private String rateName;

    @Column(name = "rate", precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal rate = BigDecimal.ZERO;

    /**
     * What the rate was applied to. Held rather than derived, because the base is a judgement: the
     * bill's net of VAT in the ordinary case, but a part of it where only part of the supply is
     * subject to withholding.
     */
    @Column(name = "base_amount", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal baseAmount = BigDecimal.ZERO;

    @Column(name = "amount", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal amount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private WithholdingStatus status = WithholdingStatus.DRAFT;

    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    /** The bill payment this became, which is what keeps the bill's balance honest. */
    @Column(name = "bill_payment_id")
    private Long billPaymentId;

    @Column(name = "notes", length = 1000)
    private String notes;

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
        return status == WithholdingStatus.DRAFT;
    }

    @Transient
    public boolean isIssued() {
        return status == WithholdingStatus.ISSUED;
    }

    @Transient
    public boolean isVoided() {
        return status == WithholdingStatus.VOID;
    }

    @Transient
    public boolean isEditable() {
        return status == WithholdingStatus.DRAFT;
    }

    /** What the supplier is actually paid once this has been taken off. */
    @Transient
    public BigDecimal getNetToSupplier() {
        return zero(baseAmount).subtract(zero(amount));
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
