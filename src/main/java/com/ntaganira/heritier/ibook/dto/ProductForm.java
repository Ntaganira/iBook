/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : ProductForm.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Product form backing bean
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductForm(
        @NotBlank(message = "{inv.prod.skuRequired}") @Size(max = 60) String sku,
        @NotBlank(message = "{inv.prod.nameRequired}") @Size(max = 200) String name,
        @Size(max = 1000) String description,
        String type,
        Long categoryId,
        Long brandId,
        @Size(max = 40) String unit,
        BigDecimal costPrice,
        BigDecimal sellingPrice,
        Long taxRateId,
        /* The excise duty this product carries, if any. Excise is charged on goods, so it
         * belongs to the product rather than to a customer or a document. */
        Long exciseDutyId,
        BigDecimal reorderLevel,
        boolean trackStock,
        boolean active) {

    public static ProductForm empty() {
        return new ProductForm("", "", null, "GOOD", null, null, "each",
                BigDecimal.ZERO, BigDecimal.ZERO, null, null, BigDecimal.ZERO, true, true);
    }
}
