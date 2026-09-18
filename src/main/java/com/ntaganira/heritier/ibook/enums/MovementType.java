/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.enums
 * - File      : MovementType.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Reasons stock moves in or out
 * </pre>
 */
package com.ntaganira.heritier.ibook.enums;

public enum MovementType {
    OPENING(1), PURCHASE(1), ADJUSTMENT_IN(1), TRANSFER_IN(1),
    SALE(-1), ADJUSTMENT_OUT(-1), TRANSFER_OUT(-1), WRITE_OFF(-1);

    private final int sign;

    MovementType(int sign) {
        this.sign = sign;
    }

    /** +1 adds to stock on hand, -1 removes from it. */
    public int sign() {
        return sign;
    }

    public boolean isInbound() {
        return sign > 0;
    }
}
