/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : ProjectBudgetController.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Project budget web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.ProjectBudgetForm;
import com.ntaganira.heritier.ibook.entity.ProjectBudget;
import com.ntaganira.heritier.ibook.entity.ProjectBudgetLine;
import com.ntaganira.heritier.ibook.enums.BudgetStatus;
import com.ntaganira.heritier.ibook.service.AuditService;
import com.ntaganira.heritier.ibook.service.ProjectBudgetService;
import com.ntaganira.heritier.ibook.service.ProjectService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/projects/budgets")
public class ProjectBudgetController {

    private final ProjectBudgetService projectBudgetService;
    private final ProjectService projectService;
    private final MessageSource messageSource;

    public ProjectBudgetController(ProjectBudgetService projectBudgetService,
                                   ProjectService projectService,
                                   MessageSource messageSource) {
        this.projectBudgetService = projectBudgetService;
        this.projectService = projectService;
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

    private void rejectWithReason(BindingResult br, RuntimeException ex) {
        String reason = ex.getMessage() == null ? msg("pbg.actionFailed") : ex.getMessage();
        br.reject("pbg.failedDetail", new Object[]{reason}, reason);
    }

    private Map<String, String> statusLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (BudgetStatus s : BudgetStatus.values()) {
            m.put(s.name(), msg("pbg.status." + s.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private void addFormContext(Model model, String mode, Long editingId, Long currentProjectId) {
        model.addAttribute("mode", mode);
        model.addAttribute("editingId", editingId);
        model.addAttribute("projects", projectBudgetService.projectsForPicker(currentProjectId));
        model.addAttribute("accounts", projectBudgetService.budgetableAccounts());
        model.addAttribute("baseCurrency", projectService.baseCurrency());
    }

    private static LocalDate startOrDefault(LocalDate from) {
        return from == null ? LocalDate.now().withDayOfYear(1) : from;
    }

    private static LocalDate endOrDefault(LocalDate to) {
        return to == null ? LocalDate.now() : to;
    }

    // ----- List -----

    @GetMapping
    public String budgets(@RequestParam(value = "from", required = false) String fromText,
                          @RequestParam(value = "to", required = false) String toText,
                          Model model) {
        LocalDate from = startOrDefault(parseDate(fromText));
        LocalDate to = endOrDefault(parseDate(toText));
        model.addAttribute("rows", projectBudgetService.listRows(from, to));
        model.addAttribute("summary", projectBudgetService.summary());
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("baseCurrency", projectService.baseCurrency());
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        return "projects/budgets";
    }

    // ----- Create / edit -----

    @GetMapping("/new")
    public String newBudget(@RequestParam(value = "project", required = false) Long projectId,
                            Model model) {
        addFormContext(model, "create", null, projectId);
        ProjectBudgetForm form = ProjectBudgetForm.empty();
        form.setProjectId(projectId);
        model.addAttribute("form", form);
        return "projects/budget-form";
    }

    @GetMapping("/{id}/edit")
    public String editBudget(@PathVariable Long id, Model model, RedirectAttributes ra) {
        ProjectBudget budget = projectBudgetService.get(id);
        if (budget == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("pbg.notFound", null));
            return "redirect:/projects/budgets";
        }
        if (!budget.isEditable()) {
            ra.addFlashAttribute("flashMessage",
                    errorFlash("pbg.notEditable", budget.getProjectCode()));
            return "redirect:/projects/budgets/" + id;
        }
        addFormContext(model, "edit", id, budget.getProjectId());
        model.addAttribute("form", toForm(budget));
        return "projects/budget-form";
    }

    private ProjectBudgetForm toForm(ProjectBudget budget) {
        ProjectBudgetForm form = new ProjectBudgetForm();
        form.setProjectId(budget.getProjectId());
        form.setDescription(budget.getDescription());
        form.setBudgetedHours(budget.getBudgetedHours());
        form.setNotes(budget.getNotes());
        int index = 0;
        for (ProjectBudgetLine line : budget.getLines()) {
            ProjectBudgetForm.Line row = form.getLines().get(index++);
            row.setAccountId(line.getAccountId());
            row.setAccountType(line.getAccountType().name());
            row.setAmount(line.getAmount());
            row.setNotes(line.getNotes());
        }
        for (int i = 0; i < 3; i++) {
            form.getLines().get(index++);
        }
        return form;
    }

    @PostMapping
    public String create(@ModelAttribute("form") ProjectBudgetForm form, BindingResult br,
                         Model model, RedirectAttributes ra) {
        try {
            ProjectBudget saved = projectBudgetService.save(form, null,
                    AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("pbg.saved", saved.getProjectCode()));
            return "redirect:/projects/budgets/" + saved.getId();
        } catch (RuntimeException ex) {
            rejectWithReason(br, ex);
            addFormContext(model, "create", null, form.getProjectId());
            return "projects/budget-form";
        }
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute("form") ProjectBudgetForm form,
                         BindingResult br, Model model, RedirectAttributes ra) {
        if (projectBudgetService.get(id) == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("pbg.notFound", null));
            return "redirect:/projects/budgets";
        }
        try {
            ProjectBudget saved = projectBudgetService.save(form, id,
                    AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("pbg.saved", saved.getProjectCode()));
            return "redirect:/projects/budgets/" + saved.getId();
        } catch (RuntimeException ex) {
            rejectWithReason(br, ex);
            addFormContext(model, "edit", id, form.getProjectId());
            return "projects/budget-form";
        }
    }

    // ----- View -----

    @GetMapping("/{id}")
    public String view(@PathVariable Long id,
                       @RequestParam(value = "from", required = false) String fromText,
                       @RequestParam(value = "to", required = false) String toText,
                       Model model, RedirectAttributes ra) {
        ProjectBudget budget = projectBudgetService.get(id);
        if (budget == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("pbg.notFound", null));
            return "redirect:/projects/budgets";
        }
        LocalDate from = startOrDefault(parseDate(fromText));
        LocalDate to = endOrDefault(parseDate(toText));
        model.addAttribute("budget", budget);
        model.addAttribute("comparison", projectBudgetService.compare(budget, from, to));
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("baseCurrency", projectService.baseCurrency());
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        return "projects/budget-view";
    }

    // ----- Lifecycle -----

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id, RedirectAttributes ra) {
        return lifecycle(id, ra, "pbg.approved",
                () -> projectBudgetService.approve(id, AuditService.currentUsername()));
    }

    @PostMapping("/{id}/reopen")
    public String reopen(@PathVariable Long id, RedirectAttributes ra) {
        return lifecycle(id, ra, "pbg.reopened", () -> projectBudgetService.reopen(id));
    }

    @PostMapping("/{id}/close")
    public String close(@PathVariable Long id, RedirectAttributes ra) {
        return lifecycle(id, ra, "pbg.closed", () -> projectBudgetService.close(id));
    }

    private String lifecycle(Long id, RedirectAttributes ra, String successKey,
                             java.util.function.Supplier<ProjectBudget> action) {
        try {
            action.get();
            ra.addFlashAttribute("flashMessage", flash(successKey, null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("pbg.actionFailed", ex.getMessage()));
        }
        return "redirect:/projects/budgets/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            projectBudgetService.delete(id);
            ra.addFlashAttribute("flashMessage", flash("pbg.deleted", null));
            return "redirect:/projects/budgets";
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("pbg.actionFailed", ex.getMessage()));
            return "redirect:/projects/budgets/" + id;
        }
    }

    private static LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
