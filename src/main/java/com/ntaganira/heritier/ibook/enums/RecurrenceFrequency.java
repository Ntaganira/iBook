/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.enums
 * - File      : RecurrenceFrequency.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : How often a recurring invoice schedule raises an invoice
 * </pre>
 */
package com.ntaganira.heritier.ibook.enums;

import java.time.LocalDate;

public enum RecurrenceFrequency {

    WEEKLY,
    FORTNIGHTLY,
    MONTHLY,
    QUARTERLY,
    SEMIANNUAL,
    ANNUAL;

    /**
     * Month-based steps clamp to the end of a shorter month, so a schedule started on the 31st
     * lands on the 28th in February rather than skipping the month.
     */
    public LocalDate next(LocalDate from) {
        return switch (this) {
            case WEEKLY -> from.plusWeeks(1);
            case FORTNIGHTLY -> from.plusWeeks(2);
            case MONTHLY -> from.plusMonths(1);
            case QUARTERLY -> from.plusMonths(3);
            case SEMIANNUAL -> from.plusMonths(6);
            case ANNUAL -> from.plusYears(1);
        };
    }
}
