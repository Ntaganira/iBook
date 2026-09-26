/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : ForecastEngine.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : The seam where a forecasting model would plug in
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * The seam a forecasting model would plug into.
 *
 * <p><strong>There is no model in this codebase</strong>, and nothing here pretends otherwise. The
 * only implementation reports itself unavailable. The forecasting that does exist is arithmetic and
 * says so on its own pages: an average of the last <em>n</em> months, a least-squares line carried
 * forward, the same month a year ago, or a copy of a budget. Calling any of that "AI" would be a
 * claim about how the numbers were produced, and a wrong one.
 *
 * <p>The same approach {@link OcrEngine} takes for text recognition and {@code DocumentStorage}
 * takes for MinIO: the door is open, the shape is fixed, and adding a real model is one class.
 *
 * <p>What is built instead is the half that would make any model worth having — a record of how
 * accurate each method has actually been. A forecast nobody scores is a guess that never learns it
 * was wrong, and adding a cleverer guess on top of that would not help.
 */
public interface ForecastEngine {

    /** Whether a model can be called right now. */
    boolean isAvailable();

    /** What to tell somebody looking at the page about the state of modelled forecasting. */
    String describe();

    /**
     * Projects twelve months from a run of monthly history.
     *
     * <p>Returns an empty list when no model is configured, rather than falling back to arithmetic
     * — the arithmetic methods already exist and are labelled for what they are, and quietly
     * substituting one for a model would misreport how a figure was arrived at.
     */
    List<BigDecimal> project(List<BigDecimal> monthlyHistory);
}
