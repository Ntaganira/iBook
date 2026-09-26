/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : ForecastAccuracyController.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : Forecast accuracy web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.enums.ForecastMethod;
import com.ntaganira.heritier.ibook.service.ForecastAccuracyService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Serves {@code /budgets/ai-forecasts}.
 *
 * <p>The route keeps its name because it is what the sidebar links to, but the page is honest about
 * what it does: no forecasting model is configured, so nothing here is modelled or learned. What it
 * provides is the accuracy record the forecasts never had.
 *
 * <p>Declared as a literal path so it wins over {@code BudgetController}'s {@code /budgets/{id}},
 * which would otherwise try to read "ai-forecasts" as an id and fail with a bad request.
 */
@Controller
@RequestMapping("/budgets/ai-forecasts")
public class ForecastAccuracyController {

    private final ForecastAccuracyService forecastAccuracyService;
    private final MessageSource messageSource;

    public ForecastAccuracyController(ForecastAccuracyService forecastAccuracyService,
                                      MessageSource messageSource) {
        this.forecastAccuracyService = forecastAccuracyService;
        this.messageSource = messageSource;
    }

    private String msg(String key) {
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
    }

    private Map<String, String> methodLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (ForecastMethod method : ForecastMethod.values()) {
            m.put(method.name(), msg("fc.method." + method.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    @GetMapping
    public String accuracy(@RequestParam(value = "asOf", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
                           Model model) {
        model.addAttribute("review", forecastAccuracyService.review(asOf));
        model.addAttribute("methodLabels", methodLabels());
        model.addAttribute("asOf", asOf == null ? LocalDate.now() : asOf);
        return "budgets/forecast-accuracy";
    }
}
