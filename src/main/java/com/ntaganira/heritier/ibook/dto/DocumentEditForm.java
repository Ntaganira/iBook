/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : DocumentEditForm.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : The details of a stored document that can be changed after upload
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

public record DocumentEditForm(String category,
                               String linkType,
                               Long linkId,
                               String description,
                               String tags,
                               Boolean template) {

    public boolean templateValue() {
        return Boolean.TRUE.equals(template);
    }
}
