/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.enums
 * - File      : ProgressBillingMethod.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : How much of a fixed price a progress claim asks for
 * </pre>
 */
package com.ntaganira.heritier.ibook.enums;

public enum ProgressBillingMethod {

    /** A share of the agreed price, given as the percentage complete to date. */
    PERCENT_COMPLETE,

    /** A flat sum — a milestone payment, a deposit, or a release of retention. */
    AMOUNT;

    public boolean isPercent() {
        return this == PERCENT_COMPLETE;
    }
}
