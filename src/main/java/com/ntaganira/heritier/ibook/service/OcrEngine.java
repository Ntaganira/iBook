/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : OcrEngine.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : The seam where a text-recognition service would plug in
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import org.springframework.core.io.Resource;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The seam a text-recognition service would plug into.
 *
 * <p>There is <strong>no engine in this codebase</strong>, and this interface does not pretend
 * otherwise: the only implementation reports itself unavailable, and the capture page says so on
 * screen rather than failing mysteriously. The same approach {@code DocumentStorage} takes for
 * MinIO — the door is open and the shape is fixed, so adding a real engine is one class and no
 * schema change.
 *
 * <p>Reading a document is the easy half and the half that can be bought. The half that decides
 * money — checking what was read, and turning it into a draft somebody has to approve — is built
 * and works whether an engine exists or not.
 */
public interface OcrEngine {

    /** Whether this engine can actually be called right now. */
    boolean isAvailable();

    /** What to tell somebody looking at the page about the state of text recognition. */
    String describe();

    /**
     * Reads what it can off a document. Every field is optional: an engine that could not find a
     * total returns null for it rather than guessing, because a guessed total that nobody notices
     * is worse than a blank one that somebody fills in.
     */
    Extraction read(Resource file, String contentType);

    record Extraction(String supplierName, String documentNo, LocalDate documentDate,
                      String currencyCode, BigDecimal subtotal, BigDecimal taxAmount,
                      BigDecimal total) {

        public static Extraction empty() {
            return new Extraction(null, null, null, null, null, null, null);
        }
    }
}
