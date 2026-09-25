/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : ProjectForm.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Project form backing record
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A record, following the convention for forms without repeating lines. {@code activateNow} is a
 * boxed {@link Boolean} because an unchecked checkbox submits nothing at all and a record cannot
 * take null for a primitive.
 */
public record ProjectForm(
        String code,
        String name,
        Long customerId,
        String description,
        String billingType,
        LocalDate startDate,
        LocalDate endDate,
        String manager,
        BigDecimal fixedPrice,
        BigDecimal defaultBillRate,
        BigDecimal defaultCostRate,
        BigDecimal estimatedHours,
        String currencyCode,
        String notes,
        Boolean activateNow) {

    public static ProjectForm empty() {
        return new ProjectForm(null, null, null, null, "TIME_AND_MATERIALS",
                LocalDate.now(), null, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, null, null, Boolean.FALSE);
    }

    public boolean activateNowValue() {
        return Boolean.TRUE.equals(activateNow);
    }

    public BigDecimal fixedPriceValue() {
        return fixedPrice == null ? BigDecimal.ZERO : fixedPrice;
    }

    public BigDecimal defaultBillRateValue() {
        return defaultBillRate == null ? BigDecimal.ZERO : defaultBillRate;
    }

    public BigDecimal defaultCostRateValue() {
        return defaultCostRate == null ? BigDecimal.ZERO : defaultCostRate;
    }

    public BigDecimal estimatedHoursValue() {
        return estimatedHours == null ? BigDecimal.ZERO : estimatedHours;
    }
}
