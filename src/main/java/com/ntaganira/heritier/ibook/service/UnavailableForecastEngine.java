/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : UnavailableForecastEngine.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : What runs while no forecasting model is configured
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * What runs while no forecasting model is configured, which is always, today.
 *
 * <p>Named for what it is. Falling back to one of the arithmetic methods would produce a figure
 * indistinguishable from a modelled one, and the page would then be reporting how a number was
 * arrived at incorrectly — which is the one thing a forecast page must not do.
 */
@Component
public class UnavailableForecastEngine implements ForecastEngine {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String describe() {
        return "No forecasting model is configured, so nothing here is modelled or learned. The "
                + "forecasts this application produces are arithmetic — an average, a straight-line "
                + "trend, last year's same month, or a copy of a budget — and each says which it "
                + "used. What this page does is score them against what the ledger then did, so no "
                + "method is trusted on faith.";
    }

    @Override
    public List<BigDecimal> project(List<BigDecimal> monthlyHistory) {
        return List.of();
    }
}
