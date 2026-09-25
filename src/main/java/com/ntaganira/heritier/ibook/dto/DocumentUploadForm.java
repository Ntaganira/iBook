/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : DocumentUploadForm.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : What is asked for alongside the file being uploaded
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

public record DocumentUploadForm(String category,
                                 String linkType,
                                 Long linkId,
                                 String description,
                                 String tags,
                                 Boolean template) {

    public boolean templateValue() {
        return Boolean.TRUE.equals(template);
    }
}
