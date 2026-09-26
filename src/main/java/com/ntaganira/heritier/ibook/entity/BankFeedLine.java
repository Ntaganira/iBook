/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : BankFeedLine.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : One imported statement line waiting to be told what it is
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.FeedLineStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One line off an imported statement, waiting to be told what it is.
 *
 * <p>An imported line is <strong>not</strong> an accounting entry. It is a claim by the bank that
 * money moved, and until somebody says which account it belongs to there is nothing to post: a
 * 40,000 debit could be rent, a repayment, a drawing or a theft, and only a person knows which.
 * That is what {@code /banking/uncategorized} is for.
 *
 * <p>Posting a line writes one balanced entry and stamps the entry number here, so a line cannot be
 * posted twice. Ignoring one records that somebody looked at it and decided it needed nothing —
 * which is different from, and much more useful than, a line nobody ever dealt with.
 */
@Entity
@Table(name = "bank_feed_lines")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "feedImport")
@ToString(exclude = "feedImport")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankFeedLine implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_id")
    private BankFeedImport feedImport;

    /**
     * The bank account this line belongs to, copied on. Kept here as well as on the import so the
     * uncategorised worklist can be read without joining back through it.
     */
    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "account_code")
    private String accountCode;

    @Column(name = "account_name")
    private String accountName;

    @Column(name = "line_date", nullable = false)
    private LocalDate lineDate;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "reference")
    private String reference;

    @Column(name = "money_in", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal moneyIn = BigDecimal.ZERO;

    @Column(name = "money_out", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal moneyOut = BigDecimal.ZERO;

    /** The balance the statement gave after this line, where the file carried one. Reference only. */
    @Column(name = "running_balance", precision = 16, scale = 2)
    private BigDecimal runningBalance;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private FeedLineStatus status = FeedLineStatus.UNCATEGORISED;

    /** What it was decided to be, once somebody said so. */
    @Column(name = "category_account_id")
    private Long categoryAccountId;

    @Column(name = "category_account_code")
    private String categoryAccountCode;

    @Column(name = "category_account_name")
    private String categoryAccountName;

    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    @Column(name = "journal_entry_no")
    private String journalEntryNo;

    @Column(name = "decided_by")
    private String decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "sort_order")
    @Builder.Default
    private int sortOrder = 0;

    @Transient
    public boolean isUncategorised() {
        return status == FeedLineStatus.UNCATEGORISED;
    }

    @Transient
    public boolean isPosted() {
        return status == FeedLineStatus.POSTED;
    }

    @Transient
    public boolean isIgnored() {
        return status == FeedLineStatus.IGNORED;
    }

    /** Positive is money arriving, negative is money leaving. */
    @Transient
    public BigDecimal getNet() {
        return zero(moneyIn).subtract(zero(moneyOut));
    }

    @Transient
    public BigDecimal getMagnitude() {
        return getNet().abs();
    }

    @Transient
    public boolean isMoneyIn() {
        return getNet().signum() > 0;
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
