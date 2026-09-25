/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : TimeEntryForm.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Timesheet form backing record
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Boxed Boolean for the checkbox: an unchecked box submits nothing, and a record cannot take null
 * for a primitive. Rates are left null on a new entry so the project can supply its own.
 */
public record TimeEntryForm(
        Long projectId,
        String person,
        LocalDate workDate,
        BigDecimal hours,
        String task,
        String description,
        Boolean billable,
        BigDecimal billRate,
        BigDecimal costRate,
        Boolean approveNow) {

    public static TimeEntryForm empty() {
        return new TimeEntryForm(null, null, LocalDate.now(), null, null, null,
                Boolean.TRUE, null, null, Boolean.FALSE);
    }

    public boolean billableValue() {
        return Boolean.TRUE.equals(billable);
    }

    public boolean approveNowValue() {
        return Boolean.TRUE.equals(approveNow);
    }

    public BigDecimal hoursValue() {
        return hours == null ? BigDecimal.ZERO : hours;
    }
}
