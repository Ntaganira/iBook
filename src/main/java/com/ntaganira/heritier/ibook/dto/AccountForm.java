/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : AccountForm.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : Chart of accounts form backing bean
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AccountForm(
        @NotBlank @Size(max = 20) String code,
        @NotBlank @Size(max = 120) String name,
        @NotNull String type,
        Long parentId,
        @Size(max = 300) String description,
        BigDecimal openingBalance,
        boolean active) {
}