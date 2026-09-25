/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : UnavailableOcrEngine.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : The stand-in used while no text-recognition service is configured
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * What runs while no text-recognition service is configured, which is always, today.
 *
 * <p>It is named for what it is. A stand-in that returned plausible-looking blanks would be
 * indistinguishable on screen from an engine that ran and found nothing, and somebody would
 * eventually conclude their receipts were unreadable rather than that nothing had read them.
 * Instead it reports itself unavailable, the page says so, and capture carries on by hand.
 */
@Component
public class UnavailableOcrEngine implements OcrEngine {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String describe() {
        return "No text-recognition service is configured, so nothing reads documents "
                + "automatically. Figures are typed in against the document instead, and "
                + "everything after that — the checks and the draft — works the same either way.";
    }

    @Override
    public Extraction read(Resource file, String contentType) {
        return Extraction.empty();
    }
}
