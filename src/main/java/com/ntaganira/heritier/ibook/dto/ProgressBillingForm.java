/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : ProgressBillingForm.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Form for raising a claim against a share of an agreed price
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProgressBillingForm(Long projectId,
                                  LocalDate claimDate,
                                  String method,
                                  BigDecimal percentComplete,
                                  BigDecimal amount,
                                  BigDecimal retentionPercent,
                                  String description,
                                  String notes) {

    public BigDecimal percentCompleteValue() {
        return percentComplete == null ? BigDecimal.ZERO : percentComplete;
    }

    public BigDecimal amountValue() {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    public BigDecimal retentionPercentValue() {
        return retentionPercent == null ? BigDecimal.ZERO : retentionPercent;
    }
}
