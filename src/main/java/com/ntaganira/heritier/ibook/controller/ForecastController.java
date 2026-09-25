/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : ForecastController.java
 * - Date      : 2026. 09. 20.
 * - User      : Hntaganira
 * - Desc      : Revenue, expense and cash flow forecast web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.ForecastForm;
import com.ntaganira.heritier.ibook.entity.Forecast;
import com.ntaganira.heritier.ibook.entity.ForecastLine;
import com.ntaganira.heritier.ibook.enums.ForecastKind;
import com.ntaganira.heritier.ibook.enums.ForecastMethod;
import com.ntaganira.heritier.ibook.enums.ForecastStatus;
import com.ntaganira.heritier.ibook.service.AuditService;
import com.ntaganira.heritier.ibook.service.CashFlowForecastService;
import com.ntaganira.heritier.ibook.service.ForecastService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/budgets")
public class ForecastController {

    private static final int MONTHS = 12;

    private final ForecastService forecastService;
    private final CashFlowForecastService cashFlowForecastService;
    private final MessageSource messageSource;

    public ForecastController(ForecastService forecastService,
                              CashFlowForecastService cashFlowForecastService,
                              MessageSource messageSource) {
        this.forecastService = forecastService;
        this.cashFlowForecastService = cashFlowForecastService;
        this.messageSource = messageSource;
    }

    private String msg(String key) {
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
    }

    private Map<String, String> flash(String key, String detail) {
        return Map.of("title", msg(key), "detail", detail == null ? "" : detail, "type", "success");
    }

    private Map<String, String> errorFlash(String key, String detail) {
        return Map.of("title", msg(key), "detail", detail == null ? "" : detail, "type", "error");
    }

    /** A resolvable error code hides the default message, so the reason goes in as an argument. */
    private void rejectWithReason(BindingResult br, RuntimeException ex) {
        String reason = ex.getMessage() == null ? msg("fc.actionFailed") : ex.getMessage();
        br.reject("fc.failedDetail", new Object[]{reason}, reason);
    }

    private Map<String, String> statusLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (ForecastStatus s : ForecastStatus.values()) {
            m.put(s.name(), msg("fc.status." + s.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private Map<String, String> methodLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (ForecastMethod method : ForecastMethod.values()) {
            m.put(method.name(), msg("fc.method." + method.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private Map<String, String> methodHints() {
        Map<String, String> m = new LinkedHashMap<>();
        for (ForecastMethod method : ForecastMethod.values()) {
            m.put(method.name(), msg("fc.methodHint." + method.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    /** Column headings follow the forecast's own first month, not January. */
    private List<String> monthLabels(LocalDate start) {
        List<String> labels = new ArrayList<>();
        Locale locale = LocaleContextHolder.getLocale();
        for (int m = 0; m < MONTHS; m++) {
            labels.add(start.plusMonths(m).getMonth().getDisplayName(TextStyle.SHORT, locale));
        }
        return labels;
    }

    private String pathFor(ForecastKind kind) {
        return kind == ForecastKind.REVENUE ? "/budgets/revenue-forecast" : "/budgets/expense-forecast";
    }

    // ----- The two forecast pages -----

    @GetMapping("/revenue-forecast")
    public String revenueForecast(@RequestParam(value = "forecast", required = false) Long id,
                                  Model model) {
        return forecastPage(ForecastKind.REVENUE, id, model);
    }

    @GetMapping("/expense-forecast")
    public String expenseForecast(@RequestParam(value = "forecast", required = false) Long id,
                                  Model model) {
        return forecastPage(ForecastKind.EXPENSE, id, model);
    }

    private String forecastPage(ForecastKind kind, Long id, Model model) {
        List<Forecast> all = forecastService.list(kind);
        Forecast chosen = id == null ? forecastService.current(kind) : forecastService.get(id);
        if (chosen != null && chosen.getKind() != kind) {
            chosen = forecastService.current(kind);
        }

        model.addAttribute("kind", kind.name());
        model.addAttribute("revenueKind", kind == ForecastKind.REVENUE);
        model.addAttribute("forecasts", all);
        model.addAttribute("chosen", chosen);
        model.addAttribute("view", forecastService.view(chosen));
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("methodLabels", methodLabels());
        model.addAttribute("baseCurrency", forecastService.baseCurrency());
        model.addAttribute("summary", forecastService.summary(kind));
        model.addAttribute("monthLabels",
                chosen == null ? List.of() : monthLabels(chosen.getStartDate()));
        model.addAttribute("selfPath", pathFor(kind));
        return "budgets/forecast";
    }

    // ----- Create / edit -----

    @GetMapping("/forecasts/new")
    public String newForecast(@RequestParam(value = "kind", defaultValue = "REVENUE") String kind,
                              Model model) {
        ForecastKind parsed = ForecastService.parseKind(kind);
        addFormContext(model, "create", null, parsed);
        model.addAttribute("form", ForecastForm.empty(parsed.name()));
        return "budgets/forecast-form";
    }

    @GetMapping("/forecasts/{id}/edit")
    public String editForecast(@PathVariable Long id, Model model, RedirectAttributes ra) {
        Forecast forecast = forecastService.get(id);
        if (forecast == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("fc.notFound", null));
            return "redirect:/budgets/revenue-forecast";
        }
        if (!forecast.isEditable()) {
            ra.addFlashAttribute("flashMessage", errorFlash("fc.notEditable", forecast.getName()));
            return "redirect:" + pathFor(forecast.getKind()) + "?forecast=" + id;
        }
        addFormContext(model, "edit", id, forecast.getKind());
        model.addAttribute("form", toForm(forecast));
        return "budgets/forecast-form";
    }

    private void addFormContext(Model model, String mode, Long editingId, ForecastKind kind) {
        model.addAttribute("mode", mode);
        model.addAttribute("editingId", editingId);
        model.addAttribute("kind", kind.name());
        model.addAttribute("revenueKind", kind == ForecastKind.REVENUE);
        model.addAttribute("accounts", forecastService.forecastableAccounts(kind));
        model.addAttribute("budgets", forecastService.budgetsForSource());
        model.addAttribute("methodLabels", methodLabels());
        model.addAttribute("methodHints", methodHints());
        model.addAttribute("baseCurrency", forecastService.baseCurrency());
        model.addAttribute("selfPath", pathFor(kind));
    }

    private ForecastForm toForm(Forecast forecast) {
        ForecastForm form = new ForecastForm();
        form.setName(forecast.getName());
        form.setKind(forecast.getKind().name());
        form.setStartDate(forecast.getStartDate());
        form.setMethod(forecast.getMethod().name());
        form.setBasisMonths(forecast.getBasisMonths());
        form.setGrowthPercent(forecast.getGrowthPercent());
        form.setSourceBudgetId(forecast.getSourceBudgetId());
        form.setDescription(forecast.getDescription());
        form.setNotes(forecast.getNotes());
        int index = 0;
        for (ForecastLine line : forecast.getLines()) {
            ForecastForm.Line row = form.getLines().get(index++);
            row.setAccountId(line.getAccountId());
            for (int m = 1; m <= MONTHS; m++) {
                row.setMonthAt(m, line.amountForMonth(m));
            }
            row.setGeneratedAmount(line.getGeneratedAmount());
            row.setHistoryAmount(line.getHistoryAmount());
            row.setNotes(line.getNotes());
        }
        // Spare rows so another account can be added without a round trip.
        for (int i = 0; i < 3; i++) {
            form.getLines().get(index++);
        }
        return form;
    }

    @PostMapping("/forecasts")
    public String create(@ModelAttribute("form") ForecastForm form,
                         @RequestParam(value = "action", required = false) String action,
                         BindingResult br, Model model, RedirectAttributes ra) {
        ForecastKind kind = ForecastService.parseKind(form.getKind());
        if ("generate".equals(action)) {
            return regenerate(form, br, model, "create", null, kind);
        }
        validate(form, br, null);
        if (br.hasErrors()) {
            addFormContext(model, "create", null, kind);
            return "budgets/forecast-form";
        }
        try {
            Forecast saved = forecastService.save(form, null, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("fc.saved", saved.getName()));
            return "redirect:" + pathFor(saved.getKind()) + "?forecast=" + saved.getId();
        } catch (RuntimeException ex) {
            rejectWithReason(br, ex);
            addFormContext(model, "create", null, kind);
            return "budgets/forecast-form";
        }
    }

    @PostMapping("/forecasts/{id}")
    public String update(@PathVariable Long id, @ModelAttribute("form") ForecastForm form,
                         @RequestParam(value = "action", required = false) String action,
                         BindingResult br, Model model, RedirectAttributes ra) {
        Forecast existing = forecastService.get(id);
        if (existing == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("fc.notFound", null));
            return "redirect:/budgets/revenue-forecast";
        }
        ForecastKind kind = existing.getKind();
        form.setKind(kind.name());
        if ("generate".equals(action)) {
            return regenerate(form, br, model, "edit", id, kind);
        }
        validate(form, br, id);
        if (br.hasErrors()) {
            addFormContext(model, "edit", id, kind);
            return "budgets/forecast-form";
        }
        try {
            Forecast saved = forecastService.save(form, id, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("fc.saved", saved.getName()));
            return "redirect:" + pathFor(saved.getKind()) + "?forecast=" + saved.getId();
        } catch (RuntimeException ex) {
            rejectWithReason(br, ex);
            addFormContext(model, "edit", id, kind);
            return "budgets/forecast-form";
        }
    }

    /**
     * Filling the grid is its own round trip rather than something save does, so the figures the
     * method produced are on screen and can be argued with before anything is stored.
     */
    private String regenerate(ForecastForm form, BindingResult br, Model model,
                              String mode, Long editingId, ForecastKind kind) {
        addFormContext(model, mode, editingId, kind);
        try {
            ForecastService.Generation generation = forecastService.generate(form);
            model.addAttribute("generation", generation);
            model.addAttribute("methodLabel",
                    msg("fc.method." + generation.method().name().toLowerCase(Locale.ROOT)));
        } catch (RuntimeException ex) {
            rejectWithReason(br, ex);
        }
        return "budgets/forecast-form";
    }

    private void validate(ForecastForm form, BindingResult br, Long excludeId) {
        if (form.getName() == null || form.getName().isBlank()) {
            br.rejectValue("name", "fc.nameRequired");
        } else if (forecastService.nameExists(form.getName(), excludeId)) {
            br.rejectValue("name", "fc.nameExists");
        }
        if (form.getStartDate() == null) {
            br.rejectValue("startDate", "fc.startRequired");
        }
    }

    // ----- Lifecycle -----

    @PostMapping("/forecasts/{id}/publish")
    public String publish(@PathVariable Long id, RedirectAttributes ra) {
        return lifecycle(id, ra, "fc.published", () ->
                forecastService.publish(id, AuditService.currentUsername()));
    }

    @PostMapping("/forecasts/{id}/unpublish")
    public String unpublish(@PathVariable Long id, RedirectAttributes ra) {
        return lifecycle(id, ra, "fc.unpublished", () -> forecastService.unpublish(id));
    }

    @PostMapping("/forecasts/{id}/archive")
    public String archive(@PathVariable Long id, RedirectAttributes ra) {
        return lifecycle(id, ra, "fc.archived", () -> forecastService.archive(id));
    }

    @PostMapping("/forecasts/{id}/restore")
    public String restore(@PathVariable Long id, RedirectAttributes ra) {
        return lifecycle(id, ra, "fc.restored", () -> forecastService.restore(id));
    }

    private String lifecycle(Long id, RedirectAttributes ra, String successKey,
                             java.util.function.Supplier<Forecast> action) {
        Forecast existing = forecastService.get(id);
        if (existing == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("fc.notFound", null));
            return "redirect:/budgets/revenue-forecast";
        }
        String target = pathFor(existing.getKind()) + "?forecast=" + id;
        try {
            action.get();
            ra.addFlashAttribute("flashMessage", flash(successKey, existing.getName()));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("fc.actionFailed", ex.getMessage()));
        }
        return "redirect:" + target;
    }

    @PostMapping("/forecasts/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        Forecast existing = forecastService.get(id);
        if (existing == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("fc.notFound", null));
            return "redirect:/budgets/revenue-forecast";
        }
        String list = pathFor(existing.getKind());
        try {
            forecastService.delete(id);
            ra.addFlashAttribute("flashMessage", flash("fc.deleted", existing.getName()));
            return "redirect:" + list;
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("fc.actionFailed", ex.getMessage()));
            return "redirect:" + list + "?forecast=" + id;
        }
    }

    // ----- Cash flow -----

    /**
     * Three different answers, so the parameter is read as text rather than as a number. Absent
     * means nobody has chosen yet and the published forecast is the sensible default; present but
     * empty is somebody choosing "none", which has to survive the round trip or the option in the
     * dropdown does nothing; anything else names a forecast, and one of the wrong kind is refused
     * rather than quietly treated as revenue.
     */
    private Forecast chosenForecast(ForecastKind kind, String id) {
        if (id == null) {
            return forecastService.current(kind);
        }
        if (id.isBlank()) {
            return null;
        }
        Forecast forecast;
        try {
            forecast = forecastService.get(Long.valueOf(id.trim()));
        } catch (NumberFormatException ignored) {
            return null;
        }
        return forecast != null && forecast.getKind() == kind ? forecast : null;
    }

    @GetMapping("/cash-flow-forecast")
    public String cashFlowForecast(
            @RequestParam(value = "start", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(value = "revenue", required = false) String revenueId,
            @RequestParam(value = "expense", required = false) String expenseId,
            @RequestParam(value = "collect", defaultValue = "1") int collectionLag,
            @RequestParam(value = "pay", defaultValue = "1") int paymentLag,
            Model model) {

        LocalDate from = start == null ? LocalDate.now().withDayOfMonth(1) : start.withDayOfMonth(1);
        Forecast revenue = chosenForecast(ForecastKind.REVENUE, revenueId);
        Forecast expense = chosenForecast(ForecastKind.EXPENSE, expenseId);

        model.addAttribute("cashFlow", cashFlowForecastService.project(from, revenue, expense,
                collectionLag, paymentLag));
        model.addAttribute("revenueForecasts", forecastService.list(ForecastKind.REVENUE));
        model.addAttribute("expenseForecasts", forecastService.list(ForecastKind.EXPENSE));
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("baseCurrency", forecastService.baseCurrency());
        model.addAttribute("start", from);
        model.addAttribute("revenueId", revenue == null ? null : revenue.getId());
        model.addAttribute("expenseId", expense == null ? null : expense.getId());
        model.addAttribute("collectionLag", collectionLag);
        model.addAttribute("paymentLag", paymentLag);
        model.addAttribute("lagChoices", List.of(0, 1, 2, 3, 4, 5, 6));
        return "budgets/cash-flow-forecast";
    }
}
