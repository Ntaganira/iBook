/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : ForecastAccuracyService.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : Scoring forecasts against what the ledger then did
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.entity.Forecast;
import com.ntaganira.heritier.ibook.entity.ForecastLine;
import com.ntaganira.heritier.ibook.enums.ForecastMethod;
import com.ntaganira.heritier.ibook.enums.ForecastStatus;
import com.ntaganira.heritier.ibook.repository.ForecastRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scores every committed forecast against what the ledger then actually did.
 *
 * <p>This is the thing that was missing. Nothing compared a published forecast back against reality,
 * so no method was ever shown to have been better than another and a forecast was a guess that
 * never learned it was wrong. Budget-versus-actual does this job for budgets; this does it for
 * forecasts.
 *
 * <p><strong>Only months that have finished are scored.</strong> A month still running has partial
 * actuals, and comparing a full month's forecast against half a month's ledger would report every
 * forecast as wildly optimistic. The cut-off is the end of the last complete month.
 *
 * <p><strong>Only committed forecasts are scored.</strong> A draft was never relied on, so holding
 * it to account would be unfair and would flatter or damn a method on figures nobody acted on.
 *
 * <p>Actuals come from {@link ReportService#signedMovementsBetween}, the same definition the profit
 * and loss uses, so a forecast cannot be scored against a number that disagrees with the reports.
 *
 * <p>Two measures are reported because they answer different questions. <em>Bias</em> is the signed
 * difference: it says whether a method runs high or low, which is the useful thing to know when
 * planning. <em>Absolute error</em> ignores direction: it says how far out the figures were
 * month by month, and a method that is wildly wrong in both directions can have almost no bias
 * while being useless — reporting only bias would hide exactly that.
 */
@Service
public class ForecastAccuracyService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final ForecastRepository forecastRepository;
    private final ReportService reportService;
    private final ForecastEngine forecastEngine;

    public ForecastAccuracyService(ForecastRepository forecastRepository,
                                   ReportService reportService,
                                   ForecastEngine forecastEngine) {
        this.forecastRepository = forecastRepository;
        this.reportService = reportService;
        this.forecastEngine = forecastEngine;
    }

    public boolean engineAvailable() {
        return forecastEngine.isAvailable();
    }

    public String engineNote() {
        return forecastEngine.describe();
    }

    /**
     * Scores every committed forecast up to the end of the last complete month.
     */
    @Transactional(readOnly = true)
    public Review review(LocalDate asOf) {
        LocalDate today = asOf == null ? LocalDate.now() : asOf;
        // The last day of the previous month: the most recent point at which every month in scope
        // is complete.
        LocalDate cutoff = today.withDayOfMonth(1).minusDays(1);

        List<Scored> scored = new ArrayList<>();
        List<Forecast> skipped = new ArrayList<>();
        for (Forecast forecast : forecastRepository.findAll()) {
            if (forecast.getStatus() == ForecastStatus.DRAFT) {
                continue;
            }
            Scored result = score(forecast, cutoff);
            if (result == null) {
                skipped.add(forecast);
            } else {
                scored.add(result);
            }
        }

        // Rolled up by method, which is the question worth asking: not "was this forecast good"
        // but "is this way of forecasting any good".
        Map<ForecastMethod, MethodRollup> byMethod = new LinkedHashMap<>();
        for (Scored s : scored) {
            MethodRollup existing = byMethod.get(s.method());
            if (existing == null) {
                byMethod.put(s.method(), new MethodRollup(s.method(), 1, s.monthsScored(),
                        s.forecastTotal(), s.actualTotal(), s.absoluteError()));
            } else {
                byMethod.put(s.method(), new MethodRollup(s.method(),
                        existing.forecasts() + 1,
                        existing.monthsScored() + s.monthsScored(),
                        existing.forecastTotal().add(s.forecastTotal()),
                        existing.actualTotal().add(s.actualTotal()),
                        existing.absoluteError().add(s.absoluteError())));
            }
        }

        return new Review(cutoff, scored, new ArrayList<>(byMethod.values()), skipped,
                forecastEngine.isAvailable(), forecastEngine.describe());
    }

    /**
     * Scores one forecast. Returns null when no month of it has finished yet — there is nothing to
     * say about a forecast whose first month is still running, and saying it anyway would be noise.
     */
    private Scored score(Forecast forecast, LocalDate cutoff) {
        LocalDate start = forecast.getStartDate();
        if (start == null) {
            return null;
        }
        LocalDate firstMonth = start.withDayOfMonth(1);
        if (firstMonth.isAfter(cutoff)) {
            return null;
        }

        int monthsElapsed = 0;
        BigDecimal forecastTotal = BigDecimal.ZERO;
        BigDecimal actualTotal = BigDecimal.ZERO;
        BigDecimal absoluteError = BigDecimal.ZERO;
        List<MonthRow> months = new ArrayList<>();

        for (int m = 1; m <= 12; m++) {
            LocalDate monthStart = firstMonth.plusMonths(m - 1L);
            if (monthStart.isAfter(cutoff)) {
                break;
            }
            LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
            monthsElapsed++;

            Map<Long, BigDecimal> actuals = reportService.signedMovementsBetween(monthStart, monthEnd);
            BigDecimal monthForecast = BigDecimal.ZERO;
            BigDecimal monthActual = BigDecimal.ZERO;
            for (ForecastLine line : forecast.getLines()) {
                BigDecimal expected = line.amountForMonth(m);
                BigDecimal happened = actuals.getOrDefault(line.getAccountId(), BigDecimal.ZERO);
                monthForecast = monthForecast.add(expected);
                monthActual = monthActual.add(happened);
                absoluteError = absoluteError.add(expected.subtract(happened).abs());
            }
            forecastTotal = forecastTotal.add(monthForecast);
            actualTotal = actualTotal.add(monthActual);
            months.add(new MonthRow(monthStart, monthForecast, monthActual,
                    monthActual.subtract(monthForecast), percentOf(monthActual.subtract(monthForecast),
                            monthForecast)));
        }

        if (monthsElapsed == 0) {
            return null;
        }

        BigDecimal bias = actualTotal.subtract(forecastTotal);
        return new Scored(forecast.getId(), forecast.getName(), forecast.getKind().name(),
                forecast.getMethod(), forecast.getStatus().name(), firstMonth, monthsElapsed,
                forecastTotal, actualTotal, bias, percentOf(bias, forecastTotal), absoluteError,
                percentOf(absoluteError, forecastTotal), months);
    }

    /** A percentage of a base, or null where the base is nothing — never a division by zero. */
    private static BigDecimal percentOf(BigDecimal amount, BigDecimal base) {
        if (base == null || base.signum() == 0) {
            return null;
        }
        return amount.multiply(HUNDRED).divide(base.abs(), 1, RoundingMode.HALF_UP);
    }

    /** One month of one forecast, compared with what happened. */
    public record MonthRow(LocalDate month, BigDecimal forecast, BigDecimal actual,
                           BigDecimal difference, BigDecimal differencePercent) {

        public boolean isOver() {
            return difference.signum() < 0;
        }
    }

    /** One committed forecast, scored over the months of it that have finished. */
    public record Scored(Long id, String name, String kind, ForecastMethod method, String status,
                         LocalDate firstMonth, int monthsScored, BigDecimal forecastTotal,
                         BigDecimal actualTotal, BigDecimal bias, BigDecimal biasPercent,
                         BigDecimal absoluteError, BigDecimal absoluteErrorPercent,
                         List<MonthRow> months) {

        /** Whether the ledger came in under what was forecast, which for revenue is the bad way. */
        public boolean isUnderForecast() {
            return bias.signum() < 0;
        }
    }

    /** How a way of forecasting has actually performed, across every forecast that used it. */
    public record MethodRollup(ForecastMethod method, int forecasts, int monthsScored,
                               BigDecimal forecastTotal, BigDecimal actualTotal,
                               BigDecimal absoluteError) {

        public BigDecimal bias() {
            return actualTotal.subtract(forecastTotal);
        }

        public BigDecimal biasPercent() {
            return percentOf(bias(), forecastTotal);
        }

        public BigDecimal absoluteErrorPercent() {
            return percentOf(absoluteError, forecastTotal);
        }
    }

    public record Review(LocalDate scoredTo, List<Scored> forecasts, List<MethodRollup> byMethod,
                         List<Forecast> tooEarly, boolean engineAvailable, String engineNote) {

        public boolean hasAnything() {
            return !forecasts.isEmpty();
        }

        public boolean hasTooEarly() {
            return !tooEarly.isEmpty();
        }

        /**
         * The method with the smallest absolute error as a share of what it forecast. Null where
         * fewer than two methods have been scored, because naming a winner out of one is not a
         * comparison.
         */
        public MethodRollup best() {
            if (byMethod.size() < 2) {
                return null;
            }
            MethodRollup best = null;
            for (MethodRollup m : byMethod) {
                BigDecimal error = m.absoluteErrorPercent();
                if (error == null) {
                    continue;
                }
                if (best == null || error.compareTo(best.absoluteErrorPercent()) < 0) {
                    best = m;
                }
            }
            return best;
        }
    }
}
