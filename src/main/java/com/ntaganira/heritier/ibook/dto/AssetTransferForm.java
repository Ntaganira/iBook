/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : AssetTransferForm.java
 * - Date      : 2026. 09. 19.
 * - User      : Hntaganira
 * - Desc      : Asset transfer form backing bean
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AssetTransferForm(
        Long assetId,
        LocalDate transferDate,
        @Size(max = 120) String toLocation,
        @Size(max = 120) String toCustodian,
        Long toAssetAccountId,
        Long toAccumulatedAccountId,
        @Size(max = 120) String reference,
        @Size(max = 500) String reason,
        @Size(max = 1000) String notes,
        /* Boxed so an unchecked box, which submits nothing, still binds. */
        Boolean completeNow) {

    public static AssetTransferForm empty() {
        return new AssetTransferForm(null, LocalDate.now(), null, null, null, null,
                null, null, null, Boolean.FALSE);
    }

    public boolean completeNowValue() {
        return Boolean.TRUE.equals(completeNow);
    }
}
