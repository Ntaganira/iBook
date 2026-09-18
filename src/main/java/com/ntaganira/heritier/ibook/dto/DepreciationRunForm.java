/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : DepreciationRunForm.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Depreciation run form backing bean
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * There are no editable lines: what each asset owes is worked out from the register, never typed.
 */
public record DepreciationRunForm(
        LocalDate periodEnd,
        @Size(max = 1000) String notes,
        /* Boxed so an unchecked box, which submits nothing, still binds. */
        Boolean postNow) {

    public static DepreciationRunForm empty() {
        return new DepreciationRunForm(LocalDate.now(), null, Boolean.FALSE);
    }

    public boolean postNowValue() {
        return Boolean.TRUE.equals(postNow);
    }
}
