/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : TimeBillingForm.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : The selection of approved hours being turned into one draft invoice
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code oneLinePerProject} rolls a project's hours into a single invoice line instead of one line
 * per day. Which is right depends on what the customer agreed to be shown, so it is asked rather
 * than assumed.
 */
public record TimeBillingForm(
        List<Long> entryIds,
        LocalDate issueDate,
        LocalDate dueDate,
        String reference,
        String customerMessage,
        Long taxRateId,
        Boolean oneLinePerProject) {

    public boolean oneLinePerProjectValue() {
        return Boolean.TRUE.equals(oneLinePerProject);
    }

    public List<Long> entryIdsValue() {
        return entryIds == null ? List.of() : entryIds;
    }
}
