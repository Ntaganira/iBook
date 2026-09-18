/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : StockAdjustmentForm.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Manual stock adjustment payload
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StockAdjustmentForm(
        @NotNull(message = "{inv.adj.productRequired}") Long productId,
        Long warehouseId,
        @NotNull String direction,
        @NotNull(message = "{inv.adj.qtyRequired}") BigDecimal quantity,
        BigDecimal unitCost,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate movementDate,
        @Size(max = 120) String reference,
        @Size(max = 500) String notes) {
}
