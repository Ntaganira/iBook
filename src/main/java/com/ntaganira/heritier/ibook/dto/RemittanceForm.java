/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : RemittanceForm.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Remittance form backing bean
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RemittanceForm(
        String authority,
        @NotNull(message = "{rem.periodStartRequired}") LocalDate periodStart,
        @NotNull(message = "{rem.periodEndRequired}") LocalDate periodEnd,
        @NotNull(message = "{rem.paymentDateRequired}") LocalDate paymentDate,
        @DecimalMin(value = "0.00", message = "{rem.amountNegative}") BigDecimal amount,
        @NotNull(message = "{rem.paymentAccountRequired}") Long paymentAccountId,
        @Size(max = 60) String declarationNo,
        @Size(max = 1000) String notes,
        /*
         * Boxed. The "save draft" button submits no payNow at all, and a record cannot bind null
         * to a primitive boolean — the save would then silently do nothing.
         */
        Boolean payNow) {

    public boolean payNowValue() {
        return Boolean.TRUE.equals(payNow);
    }

    public static RemittanceForm empty(String authority) {
        LocalDate today = LocalDate.now();
        LocalDate lastMonth = today.minusMonths(1);
        return new RemittanceForm(authority == null ? "RRA_PAYE" : authority,
                lastMonth.withDayOfMonth(1),
                lastMonth.withDayOfMonth(lastMonth.lengthOfMonth()),
                today, BigDecimal.ZERO, null, null, null, Boolean.FALSE);
    }

    public BigDecimal amountValue() {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
