/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : AssetCategoryForm.java
 * - Date      : 2026. 09. 19.
 * - User      : Hntaganira
 * - Desc      : Asset category form backing bean
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AssetCategoryForm(
        @NotBlank(message = "{ast.cat.nameRequired}") @Size(max = 120) String name,
        @Size(max = 40) String code,
        @Size(max = 500) String description,
        String depreciationMethod,
        Integer usefulLifeYears,
        BigDecimal decliningRate,
        Long assetAccountId,
        Long accumulatedAccountId,
        Long expenseAccountId,
        /* Boxed so an unchecked box, which submits nothing, still binds. */
        Boolean active) {

    public static AssetCategoryForm empty() {
        return new AssetCategoryForm(null, null, null, "STRAIGHT_LINE", 5, BigDecimal.ZERO,
                null, null, null, Boolean.TRUE);
    }

    public boolean activeValue() {
        return Boolean.TRUE.equals(active);
    }

    public int usefulLifeValue() {
        return usefulLifeYears == null || usefulLifeYears < 0 ? 0 : usefulLifeYears;
    }

    public BigDecimal decliningRateOrZero() {
        return decliningRate == null ? BigDecimal.ZERO : decliningRate;
    }
}
