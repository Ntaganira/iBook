/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : FixedAssetForm.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Fixed asset form backing bean
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FixedAssetForm(
        @Size(max = 200) String name,
        @Size(max = 1000) String description,
        @Size(max = 120) String category,
        @Size(max = 120) String location,
        @Size(max = 120) String custodian,
        @Size(max = 80) String serialNumber,
        @Size(max = 80) String tagNumber,
        Long vendorId,
        @Size(max = 120) String purchaseReference,
        LocalDate acquisitionDate,
        BigDecimal acquisitionCost,
        BigDecimal residualValue,
        String depreciationMethod,
        Integer usefulLifeYears,
        BigDecimal decliningRate,
        LocalDate depreciationStart,
        BigDecimal openingAccumulated,
        Long assetAccountId,
        Long accumulatedAccountId,
        Long expenseAccountId,
        @Size(max = 1000) String notes,
        /* Boxed so an unchecked box, which submits nothing, still binds. */
        Boolean activateNow) {

    public static FixedAssetForm empty() {
        return new FixedAssetForm(null, null, null, null, null, null, null, null, null,
                LocalDate.now(), BigDecimal.ZERO, BigDecimal.ZERO, "STRAIGHT_LINE", 5,
                BigDecimal.ZERO, LocalDate.now(), BigDecimal.ZERO, null, null, null, null,
                Boolean.FALSE);
    }

    public boolean activateNowValue() {
        return Boolean.TRUE.equals(activateNow);
    }

    public BigDecimal costValue() {
        return acquisitionCost == null ? BigDecimal.ZERO : acquisitionCost;
    }

    public BigDecimal residualValueOrZero() {
        return residualValue == null ? BigDecimal.ZERO : residualValue;
    }

    public BigDecimal openingAccumulatedOrZero() {
        return openingAccumulated == null ? BigDecimal.ZERO : openingAccumulated;
    }

    public int usefulLifeValue() {
        return usefulLifeYears == null || usefulLifeYears < 0 ? 0 : usefulLifeYears;
    }

    public BigDecimal decliningRateOrZero() {
        return decliningRate == null ? BigDecimal.ZERO : decliningRate;
    }
}
