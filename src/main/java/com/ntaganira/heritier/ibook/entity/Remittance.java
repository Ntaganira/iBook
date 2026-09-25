/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : Remittance.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : A payment of payroll deductions over to the body they are owed to
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.RemittanceAuthority;
import com.ntaganira.heritier.ibook.enums.RemittanceStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Money taken off pay and handed over to whoever it belongs to.
 *
 * <p>Posting a payroll run only recognises what is <em>owed</em>: PAYE, RSSB and CBHI sit as
 * liabilities until somebody actually pays them. This is the payment. It debits the liability and
 * credits the bank, which is the entry that clears the payable — without it the balance sheet goes
 * on carrying deductions that left the building months ago.
 *
 * <p>The amount is deliberately <strong>typed in</strong> rather than forced to equal what the
 * runs computed. A declaration can be filed for a different figure than the ledger holds — a
 * penalty, an adjustment, a correction to an earlier month — and a screen that refused to record
 * what was really paid would leave the books further from the truth, not closer. The difference
 * against the runs is shown beside it instead.
 */
@Entity
@Table(name = "remittances")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Remittance implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "authority", nullable = false)
    @Builder.Default
    private RemittanceAuthority authority = RemittanceAuthority.RRA_PAYE;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "amount", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal amount = BigDecimal.ZERO;

    /** What the posted runs in this period say is owed, kept so the two can be told apart later. */
    @Column(name = "expected_amount", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal expectedAmount = BigDecimal.ZERO;

    @Column(name = "liability_account_id")
    private Long liabilityAccountId;

    @Column(name = "liability_account_code")
    private String liabilityAccountCode;

    @Column(name = "liability_account_name")
    private String liabilityAccountName;

    @Column(name = "payment_account_id")
    private Long paymentAccountId;

    @Column(name = "payment_account_code")
    private String paymentAccountCode;

    @Column(name = "payment_account_name")
    private String paymentAccountName;

    /** The declaration or receipt number the authority gave back. */
    @Column(name = "declaration_no")
    private String declarationNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private RemittanceStatus status = RemittanceStatus.DRAFT;

    @Column(name = "journal_entry_id")
    private Long journalEntryId;

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
        return status == RemittanceStatus.DRAFT;
    }

    @Transient
    public boolean isPaid() {
        return status == RemittanceStatus.PAID;
    }

    @Transient
    public boolean isVoided() {
        return status == RemittanceStatus.VOID;
    }

    @Transient
    public boolean isEditable() {
        return status == RemittanceStatus.DRAFT;
    }

    /** Positive means more was paid than the runs computed. */
    @Transient
    public BigDecimal getDifference() {
        return zero(amount).subtract(zero(expectedAmount));
    }

    @Transient
    public boolean isMatchingExpected() {
        return getDifference().signum() == 0;
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
