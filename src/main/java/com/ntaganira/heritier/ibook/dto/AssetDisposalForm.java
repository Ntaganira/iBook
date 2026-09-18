/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : AssetDisposalForm.java
 * - Date      : 2026. 09. 19.
 * - User      : Hntaganira
 * - Desc      : Asset disposal form backing bean
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AssetDisposalForm(
        Long assetId,
        LocalDate disposalDate,
        String method,
        @Size(max = 200) String buyer,
        @Size(max = 120) String reference,
        BigDecimal proceeds,
        Long proceedsAccountId,
        Long gainAccountId,
        Long lossAccountId,
        @Size(max = 1000) String notes,
        /* Boxed so an unchecked box, which submits nothing, still binds. */
        Boolean postNow) {

    public static AssetDisposalForm empty() {
        return new AssetDisposalForm(null, LocalDate.now(), "SOLD", null, null,
                BigDecimal.ZERO, null, null, null, null, Boolean.FALSE);
    }

    public boolean postNowValue() {
        return Boolean.TRUE.equals(postNow);
    }

    public BigDecimal proceedsValue() {
        return proceeds == null ? BigDecimal.ZERO : proceeds;
    }
}
