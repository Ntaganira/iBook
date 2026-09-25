/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : RecurringJournalLine.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : JPA entity for one line of a recurring journal schedule
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "recurring_journal_lines")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "recurringJournal")
@ToString(exclude = "recurringJournal")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringJournalLine implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private RecurringJournal recurringJournal;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    /**
     * The code and name as they read when the schedule was saved. Re-read from the chart at
     * generation time, so a renamed account reaches the ledger under its current name rather than
     * the one the schedule happens to remember.
     */
    @Column(name = "account_code", nullable = false)
    private String accountCode;

    @Column(name = "account_name", nullable = false)
    private String accountName;

    @Column(name = "memo")
    private String memo;

    @Column(name = "debit", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal debit = BigDecimal.ZERO;

    @Column(name = "credit", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal credit = BigDecimal.ZERO;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    public BigDecimal getDebitValue() {
        return debit == null ? BigDecimal.ZERO : debit;
    }

    public BigDecimal getCreditValue() {
        return credit == null ? BigDecimal.ZERO : credit;
    }
}
