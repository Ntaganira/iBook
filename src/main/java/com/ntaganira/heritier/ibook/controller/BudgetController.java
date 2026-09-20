/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : BudgetController.java
 * - Date      : 2026. 09. 20.
 * - User      : Hntaganira
 * - Desc      : Budgets and budget-against-actual web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.BudgetForm;
import com.ntaganira.heritier.ibook.entity.Budget;
import com.ntaganira.heritier.ibook.entity.BudgetLine;
import com.ntaganira.heritier.ibook.enums.BudgetStatus;
import com.ntaganira.heritier.ibook.service.AuditService;
import com.ntaganira.heritier.ibook.service.BudgetService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/budgets")
public class BudgetController {

    private static final int PAGE_SIZE = 20;
    private static final int MONTHS = 12;

    private final BudgetService budgetService;
    private final MessageSource messageSource;

    public BudgetController(BudgetService budgetService, MessageSource messageSource) {
        this.budgetService = budgetService;
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
        String reason = ex.getMessage() == null ? msg("bud.actionFailed") : ex.getMessage();
        br.reject("bud.failedDetail", new Object[]{reason}, reason);
    }

    private Map<String, String> statusLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (BudgetStatus s : BudgetStatus.values()) {
            m.put(s.name(), msg("bud.status." + s.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    /**
     * Column headings for the twelve months a budget covers. They follow the budget's own start
     * month, so a July-to-June fiscal year reads July first rather than January.
     */
    private List<String> monthLabels(Budget budget) {
        List<String> labels = new ArrayList<>();
        Locale locale = LocaleContextHolder.getLocale();
        for (int m = 0; m < MONTHS; m++) {
            labels.add(budget.getStartDate().plusMonths(m)
                    .getMonth().getDisplayName(TextStyle.SHORT, locale));
        }
        return labels;
    }

    private void addFormContext(Model model, String mode, Long editingId) {
        model.addAttribute("mode", mode);
        model.addAttribute("editingId", editingId);
        model.addAttribute("accounts", budgetService.budgetableAccounts());
        model.addAttribute("baseCurrency", budgetService.baseCurrency());
    }

    // ----- List -----

    @GetMapping
    public String budgets(@RequestParam(value = "q", required = false) String q,
                          @RequestParam(value = "status", required = false) String status,
                          @RequestParam(value = "year", required = false) Integer year,
                          @RequestParam(value = "sort", defaultValue = "fiscalYear") String sort,
                          @RequestParam(value = "dir", defaultValue = "desc") String dir,
                          @RequestParam(value = "page", defaultValue = "0") int page,
                          Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "fiscalYear", "fiscalYear", "name",
                "startDate", "totalRevenue", "totalExpense", "status");
        model.addAttribute("budgets", budgetService.list(q, status, year,
                PageRequest.of(page, PAGE_SIZE, sp.sort())));
        model.addAttribute("summary", budgetService.summary());
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("years", budgetService.fiscalYears());
        model.addAttribute("baseCurrency", budgetService.baseCurrency());
        model.addAttribute("q", q);
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("year", year);
        StringBuilder fq = new StringBuilder();
        if (q != null && !q.isBlank()) {
            fq.append("q=").append(UriUtils.encodeQueryParam(q, StandardCharsets.UTF_8));
        }
        if (status != null && !status.isBlank()) {
            if (!fq.isEmpty()) {
                fq.append('&');
            }
            fq.append("status=").append(status);
        }
        if (year != null) {
            if (!fq.isEmpty()) {
                fq.append('&');
            }
            fq.append("year=").append(year);
        }
        SortSpec.addListContext(model, "/budgets", fq.isEmpty() ? "" : "?" + fq, sp);
        return "budgets/budgets";
    }

    // ----- Create / edit -----

    @GetMapping("/new")
    public String newBudget(Model model) {
        addFormContext(model, "create", null);
        model.addAttribute("form", BudgetForm.empty());
        return "budgets/budget-form";
    }

    @GetMapping("/{id}/edit")
    public String editBudget(@PathVariable Long id, Model model, RedirectAttributes ra) {
        Budget budget = budgetService.get(id);
        if (budget == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("bud.notFound", null));
            return "redirect:/budgets";
        }
        if (!budget.isEditable()) {
            ra.addFlashAttribute("flashMessage", errorFlash("bud.notEditable", budget.getName()));
            return "redirect:/budgets/" + id;
        }
        addFormContext(model, "edit", id);
        model.addAttribute("form", toForm(budget));
        return "budgets/budget-form";
    }

    private BudgetForm toForm(Budget budget) {
        BudgetForm form = new BudgetForm();
        form.setName(budget.getName());
        form.setFiscalYear(budget.getFiscalYear());
        form.setStartDate(budget.getStartDate());
        form.setDescription(budget.getDescription());
        form.setNotes(budget.getNotes());
        int index = 0;
        for (BudgetLine line : budget.getLines()) {
            BudgetForm.Line row = form.getLines().get(index++);
            row.setAccountId(line.getAccountId());
            row.setAccountType(line.getAccountType().name());
            row.setAnnualAmount(line.getAnnualAmount());
            row.setM1(line.getM1());
            row.setM2(line.getM2());
            row.setM3(line.getM3());
            row.setM4(line.getM4());
            row.setM5(line.getM5());
            row.setM6(line.getM6());
            row.setM7(line.getM7());
            row.setM8(line.getM8());
            row.setM9(line.getM9());
            row.setM10(line.getM10());
            row.setM11(line.getM11());
            row.setM12(line.getM12());
            row.setNotes(line.getNotes());
        }
        // Spare rows so another account can be added without a round trip.
        for (int i = 0; i < 3; i++) {
            form.getLines().get(index++);
        }
        return form;
    }

    @PostMapping
    public String create(@ModelAttribute("form") BudgetForm form, BindingResult br,
                         Model model, RedirectAttributes ra) {
        validate(form, br, null);
        if (br.hasErrors()) {
            addFormContext(model, "create", null);
            return "budgets/budget-form";
        }
        try {
            Budget saved = budgetService.save(form, null, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("bud.saved", saved.getName()));
            return "redirect:/budgets/" + saved.getId();
        } catch (RuntimeException ex) {
            rejectWithReason(br, ex);
            addFormContext(model, "create", null);
            return "budgets/budget-form";
        }
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute("form") BudgetForm form,
                         BindingResult br, Model model, RedirectAttributes ra) {
        if (budgetService.get(id) == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("bud.notFound", null));
            return "redirect:/budgets";
        }
        validate(form, br, id);
        if (br.hasErrors()) {
            addFormContext(model, "edit", id);
            return "budgets/budget-form";
        }
        try {
            Budget saved = budgetService.save(form, id, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("bud.saved", saved.getName()));
            return "redirect:/budgets/" + saved.getId();
        } catch (RuntimeException ex) {
            rejectWithReason(br, ex);
            addFormContext(model, "edit", id);
            return "budgets/budget-form";
        }
    }

    private void validate(BudgetForm form, BindingResult br, Long excludeId) {
        if (form.getName() == null || form.getName().isBlank()) {
            br.rejectValue("name", "bud.nameRequired");
        } else if (budgetService.nameExists(form.getName(), excludeId)) {
            br.rejectValue("name", "bud.nameExists");
        }
        if (form.getStartDate() == null) {
            br.rejectValue("startDate", "bud.startRequired");
        }
    }

    // ----- View -----

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model, RedirectAttributes ra) {
        Budget budget = budgetService.get(id);
        if (budget == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("bud.notFound", null));
            return "redirect:/budgets";
        }
        model.addAttribute("budget", budget);
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("monthLabels", monthLabels(budget));
        model.addAttribute("baseCurrency", budgetService.baseCurrency());
        return "budgets/budget-view";
    }

    // ----- Lifecycle -----

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id, RedirectAttributes ra) {
        try {
            budgetService.approve(id, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("bud.approved", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("bud.actionFailed", ex.getMessage()));
        }
        return "redirect:/budgets/" + id;
    }

    @PostMapping("/{id}/reopen")
    public String reopen(@PathVariable Long id, RedirectAttributes ra) {
        try {
            budgetService.reopen(id);
            ra.addFlashAttribute("flashMessage", flash("bud.reopened", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("bud.actionFailed", ex.getMessage()));
        }
        return "redirect:/budgets/" + id;
    }

    @PostMapping("/{id}/close")
    public String close(@PathVariable Long id, RedirectAttributes ra) {
        try {
            budgetService.close(id);
            ra.addFlashAttribute("flashMessage", flash("bud.closed", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("bud.actionFailed", ex.getMessage()));
        }
        return "redirect:/budgets/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            budgetService.delete(id);
            ra.addFlashAttribute("flashMessage", flash("bud.deleted", null));
            return "redirect:/budgets";
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("bud.actionFailed", ex.getMessage()));
            return "redirect:/budgets/" + id;
        }
    }

    // ----- Budget against actual -----

    @GetMapping("/vs-actual")
    public String vsActual(@RequestParam(value = "budget", required = false) Long budgetId,
                           @RequestParam(value = "from", defaultValue = "1") int fromMonth,
                           @RequestParam(value = "to", defaultValue = "12") int toMonth,
                           Model model) {
        List<Budget> all = budgetService.all();
        Budget chosen = budgetId == null
                ? all.stream().filter(Budget::isApproved).findFirst().orElse(
                        all.isEmpty() ? null : all.get(0))
                : budgetService.get(budgetId);
        if (chosen != null && chosen.getLines().isEmpty()) {
            chosen = budgetService.get(chosen.getId());
        }

        model.addAttribute("budgets", all);
        model.addAttribute("chosen", chosen);
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("baseCurrency", budgetService.baseCurrency());
        model.addAttribute("fromMonth", fromMonth);
        model.addAttribute("toMonth", toMonth);
        model.addAttribute("monthLabels", chosen == null ? List.of() : monthLabels(chosen));
        model.addAttribute("comparison", chosen == null ? null
                : budgetService.compare(chosen, fromMonth, toMonth));
        return "budgets/vs-actual";
    }
}
