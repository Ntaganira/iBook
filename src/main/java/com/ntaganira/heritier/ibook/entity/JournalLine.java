/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : JournalLine.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : JPA entity for a journal entry line (debit or credit)
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "journal_lines")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalLine implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id", nullable = false)
    private JournalEntry entry;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "account_code", nullable = false)
    private String accountCode;

    @Column(name = "account_name", nullable = false)
    private String accountName;

    @Column(name = "memo")
    private String memo;

    @Column(name = "debit", precision = 16, scale = 2)
    private BigDecimal debit;

    @Column(name = "credit", precision = 16, scale = 2)
    private BigDecimal credit;

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