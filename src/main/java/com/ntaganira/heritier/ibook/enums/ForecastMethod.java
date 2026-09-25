/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.enums
 * - File      : ForecastMethod.java
 * - Date      : 2026. 09. 20.
 * - User      : Hntaganira
 * - Desc      : How a forecast's opening figures are worked out from what already happened
 * </pre>
 */
package com.ntaganira.heritier.ibook.enums;

public enum ForecastMethod {

    AVERAGE,
    TREND,
    SEASONAL,
    BUDGET,
    MANUAL;

    public boolean usesHistory() {
        return this == AVERAGE || this == TREND || this == SEASONAL;
    }
}
