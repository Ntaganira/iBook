/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.enums
 * - File      : ExtractionSource.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Enum of where a captured document figure came from
 * </pre>
 */
package com.ntaganira.heritier.ibook.enums;

/**
 * Whether a figure was read off the page by a machine or typed by a person.
 *
 * <p>Kept per document rather than inferred, because the two deserve different trust: a typed
 * figure was looked at by somebody, and a machine-read one was not.
 */
public enum ExtractionSource {
    TYPED, ENGINE
}
