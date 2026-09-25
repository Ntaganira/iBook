/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : PayrollRunForm.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Payroll run form backing bean
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record PayrollRunForm(
        @Size(max = 160) String name,
        @NotNull(message = "{pry.periodStartRequired}") LocalDate periodStart,
        @NotNull(message = "{pry.periodEndRequired}") LocalDate periodEnd,
        @NotNull(message = "{pry.payDateRequired}") LocalDate payDate,
        @Size(max = 120) String department,
        @Size(max = 1000) String notes,
        /*
         * Left empty the run covers everybody payable in the period. Naming people is for the
         * off-cycle case — one late starter, a single correction — and is deliberately explicit,
         * since a run that silently missed somebody looks exactly like one that did not.
         */
        List<Long> employeeIds) {

    public static PayrollRunForm empty() {
        LocalDate today = LocalDate.now();
        LocalDate start = today.withDayOfMonth(1);
        LocalDate end = today.withDayOfMonth(today.lengthOfMonth());
        return new PayrollRunForm(null, start, end, end, null, null, List.of());
    }

    public List<Long> employeeIdsValue() {
        return employeeIds == null ? List.of() : employeeIds;
    }

    public boolean coversEverybody() {
        return employeeIdsValue().isEmpty();
    }
}
