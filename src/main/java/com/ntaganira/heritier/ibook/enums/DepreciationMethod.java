/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.enums
 * - File      : DepreciationMethod.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : How an asset is written down over its life
 * </pre>
 */
package com.ntaganira.heritier.ibook.enums;

public enum DepreciationMethod {

    /** Equal instalments over the useful life. */
    STRAIGHT_LINE,

    /** A fixed percentage of what is left, so the charge falls every year. */
    REDUCING_BALANCE,

    /** Land, and anything else held at cost. */
    NONE
}
