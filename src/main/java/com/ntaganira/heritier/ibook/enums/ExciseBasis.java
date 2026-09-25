/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.enums
 * - File      : ExciseBasis.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : Enum of what an excise duty is charged on
 * </pre>
 */
package com.ntaganira.heritier.ibook.enums;

/**
 * What the duty is charged on. A percentage of value and a fixed amount per unit are not
 * interchangeable: Rwandan excise uses both depending on the good, and a system offering only one
 * would silently mis-charge the other half.
 */
public enum ExciseBasis {
    PERCENT_OF_VALUE, AMOUNT_PER_UNIT
}
