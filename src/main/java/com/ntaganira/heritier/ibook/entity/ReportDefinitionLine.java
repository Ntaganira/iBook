/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : ReportDefinitionLine.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : One row in a saved report
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.AccountType;
import com.ntaganira.heritier.ibook.enums.ReportRowType;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

/**
 * One row of a saved report.
 *
 * <p>An {@code ACCOUNTS} row selects by <strong>account type, code range, or both</strong> rather
 * than by naming accounts one at a time. A report that lists account ids goes quietly wrong the
 * day somebody adds account 5009 — it is simply left out, and nothing says so. A range picks the
 * new account up, and the report's own unassigned-accounts check catches anything still missed.
 */
@Entity
@Table(name = "report_definition_lines")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "definition")
@ToString(exclude = "definition")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportDefinitionLine implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "definition_id")
    private ReportDefinition definition;

    @Enumerated(EnumType.STRING)
    @Column(name = "row_type", nullable = false)
    @Builder.Default
    private ReportRowType rowType = ReportRowType.ACCOUNTS;

    @Column(name = "label")
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type")
    private AccountType accountType;

    @Column(name = "code_from")
    private String codeFrom;

    @Column(name = "code_to")
    private String codeTo;

    /**
     * Whether the accounts on this row are listed individually or rolled into one figure. A cost
     * of sales line on a summary report wants one number; the same accounts on a detailed review
     * want them itemised, and it is the same selection either way.
     */
    @Column(name = "expanded", nullable = false)
    @Builder.Default
    private boolean expanded = true;

    /**
     * Flips the sign of this row. Needed because the ledger's natural direction is not always the
     * reading direction: a contra-revenue account sits with revenue but reduces it, and a report
     * that showed it as a positive addition would overstate income.
     */
    @Column(name = "invert_sign", nullable = false)
    @Builder.Default
    private boolean invertSign = false;

    @Column(name = "sort_order")
    @Builder.Default
    private int sortOrder = 0;

    @Transient
    public boolean isAccountsRow() {
        return rowType == ReportRowType.ACCOUNTS;
    }

    @Transient
    public boolean isSubtotal() {
        return rowType == ReportRowType.SUBTOTAL;
    }

    /** Whether this row selects anything at all; an accounts row with no criteria selects nothing. */
    @Transient
    public boolean isSelecting() {
        return rowType == ReportRowType.ACCOUNTS
                && (accountType != null || notBlank(codeFrom) || notBlank(codeTo));
    }

    /** Whether an account's type and code fall inside this row's selection. */
    public boolean matches(AccountType type, String code) {
        if (accountType != null && accountType != type) {
            return false;
        }
        if (code == null) {
            return false;
        }
        // Codes are compared as text, which is right while they are fixed-width digits — the
        // convention the whole chart already relies on for "10xx is cash".
        if (notBlank(codeFrom) && code.compareToIgnoreCase(codeFrom.trim()) < 0) {
            return false;
        }
        return !(notBlank(codeTo) && code.compareToIgnoreCase(codeTo.trim()) > 0);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
