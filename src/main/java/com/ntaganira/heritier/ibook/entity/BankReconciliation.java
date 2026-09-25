/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : BankReconciliation.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : One statement reconciled against the ledger
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.ReconciliationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One bank statement, reconciled against what the books say.
 *
 * <p>Reconciling is <strong>entirely non-posting</strong>. It changes no figure anywhere: it
 * establishes that the ledger and the bank agree, and where they do not, why. That is the whole
 * value of it — a reconciliation that quietly adjusted the books to match the bank would destroy
 * the very disagreement it exists to find.
 *
 * <p>The statement is <strong>typed in</strong>. An automatic feed is a convenience, not the
 * mechanism; reconciliation has always worked from the piece of paper the bank sends, and waiting
 * for a feed integration would mean no reconciliation at all.
 */
@Entity
@Table(name = "bank_reconciliations")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "lines")
@ToString(exclude = "lines")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankReconciliation implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false)
    private String reference;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "account_code")
    private String accountCode;

    @Column(name = "account_name")
    private String accountName;

    /** The date the statement runs to. Everything in the books up to here is in scope. */
    @Column(name = "statement_date", nullable = false)
    private LocalDate statementDate;

    @Column(name = "opening_balance", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal openingBalance = BigDecimal.ZERO;

    /** What the bank says the account stood at. The figure everything else is measured against. */
    @Column(name = "closing_balance", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal closingBalance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ReconciliationStatus status = ReconciliationStatus.DRAFT;

    @Column(name = "completed_by")
    private String completedBy;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "reconciliation", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @OrderBy("lineDate asc, id asc")
    private List<BankStatementLine> lines = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addLine(BankStatementLine line) {
        line.setReconciliation(this);
        lines.add(line);
    }

    @Transient
    public boolean isDraft() {
        return status == ReconciliationStatus.DRAFT;
    }

    @Transient
    public boolean isCompleted() {
        return status == ReconciliationStatus.COMPLETED;
    }

    @Transient
    public boolean isVoided() {
        return status == ReconciliationStatus.VOID;
    }

    @Transient
    public boolean isEditable() {
        return status == ReconciliationStatus.DRAFT;
    }

    /** What the statement itself says its movement adds up to. */
    @Transient
    public BigDecimal getStatementMovement() {
        BigDecimal total = BigDecimal.ZERO;
        for (BankStatementLine line : lines) {
            total = total.add(line.getNet());
        }
        return total;
    }

    /**
     * Whether the statement's own figures are internally consistent — opening plus its movement
     * reaching its closing. If they do not, the statement has been typed in wrongly and there is
     * no point comparing it to anything.
     */
    @Transient
    public boolean isStatementConsistent() {
        return zero(openingBalance).add(getStatementMovement()).compareTo(zero(closingBalance)) == 0;
    }

    @Transient
    public BigDecimal getStatementDifference() {
        return zero(closingBalance).subtract(zero(openingBalance).add(getStatementMovement()));
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
