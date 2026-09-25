/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.enums
 * - File      : ProjectBillingType.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : How a project is charged to the customer
 * </pre>
 */
package com.ntaganira.heritier.ibook.enums;

public enum ProjectBillingType {

    TIME_AND_MATERIALS,
    FIXED_PRICE,
    NON_BILLABLE;

    /**
     * Only time and materials turns hours into money. A fixed price is already agreed, so billing
     * its hours as well would charge the customer twice for the same work.
     */
    public boolean isHourlyBillable() {
        return this == TIME_AND_MATERIALS;
    }
}
