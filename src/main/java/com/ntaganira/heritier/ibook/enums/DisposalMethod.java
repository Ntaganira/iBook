/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.enums
 * - File      : DisposalMethod.java
 * - Date      : 2026. 09. 19.
 * - User      : Hntaganira
 * - Desc      : How an asset left the business
 * </pre>
 */
package com.ntaganira.heritier.ibook.enums;

public enum DisposalMethod {

    SOLD,
    TRADED_IN,
    SCRAPPED,
    DONATED,
    LOST;

    /**
     * Something sold or traded leaves the register as disposed of; something scrapped, given away or
     * lost is written off. The distinction is only about how the register reads afterwards — the
     * posting is the same either way.
     */
    public AssetStatus resultingStatus() {
        return switch (this) {
            case SOLD, TRADED_IN -> AssetStatus.DISPOSED;
            case SCRAPPED, DONATED, LOST -> AssetStatus.WRITTEN_OFF;
        };
    }

    /** Only a sale or a trade-in is expected to bring anything in. */
    public boolean expectsProceeds() {
        return this == SOLD || this == TRADED_IN;
    }
}
