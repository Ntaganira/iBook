/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : ProjectController.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Project register web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.ProjectForm;
import com.ntaganira.heritier.ibook.entity.Project;
import com.ntaganira.heritier.ibook.enums.ProjectBillingType;
import com.ntaganira.heritier.ibook.enums.ProjectStatus;
import com.ntaganira.heritier.ibook.service.AuditService;
import com.ntaganira.heritier.ibook.service.ProjectService;
import com.ntaganira.heritier.ibook.service.TimeEntryService;
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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/projects")
public class ProjectController {

    private static final int PAGE_SIZE = 20;

    private final ProjectService projectService;
    private final TimeEntryService timeEntryService;
    private final MessageSource messageSource;

    public ProjectController(ProjectService projectService,
                             TimeEntryService timeEntryService,
                             MessageSource messageSource) {
        this.projectService = projectService;
        this.timeEntryService = timeEntryService;
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
        String reason = ex.getMessage() == null ? msg("prj.actionFailed") : ex.getMessage();
        br.reject("prj.failedDetail", new Object[]{reason}, reason);
    }

    private Map<String, String> statusLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (ProjectStatus s : ProjectStatus.values()) {
            m.put(s.name(), msg("prj.status." + s.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private Map<String, String> billingLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (ProjectBillingType b : ProjectBillingType.values()) {
            m.put(b.name(), msg("prj.billing." + b.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private Map<String, String> billingHints() {
        Map<String, String> m = new LinkedHashMap<>();
        for (ProjectBillingType b : ProjectBillingType.values()) {
            m.put(b.name(), msg("prj.billingHint." + b.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private void addFormContext(Model model, String mode, Long editingId, Long currentCustomerId) {
        model.addAttribute("mode", mode);
        model.addAttribute("editingId", editingId);
        model.addAttribute("customers", projectService.customersForPicker(currentCustomerId));
        model.addAttribute("billingLabels", billingLabels());
        model.addAttribute("billingHints", billingHints());
        model.addAttribute("baseCurrency", projectService.baseCurrency());
        model.addAttribute("nextCode", projectService.previewNextCode());
    }

    // ----- List -----

    @GetMapping
    public String projects(@RequestParam(value = "q", required = false) String q,
                           @RequestParam(value = "status", required = false) String status,
                           @RequestParam(value = "customer", required = false) Long customerId,
                           @RequestParam(value = "sort", defaultValue = "code") String sort,
                           @RequestParam(value = "dir", defaultValue = "asc") String dir,
                           @RequestParam(value = "page", defaultValue = "0") int page,
                           Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "code", "code", "name", "customerName",
                "startDate", "endDate", "status", "billingType");
        model.addAttribute("projects", projectService.list(q, status, customerId,
                PageRequest.of(page, PAGE_SIZE, sp.sort())));
        model.addAttribute("summary", projectService.summary());
        model.addAttribute("timeSummary", timeEntryService.summary());
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("billingLabels", billingLabels());
        model.addAttribute("customers", projectService.customersForPicker(customerId));
        model.addAttribute("baseCurrency", projectService.baseCurrency());
        model.addAttribute("q", q);
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("customerId", customerId);

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
        if (customerId != null) {
            if (!fq.isEmpty()) {
                fq.append('&');
            }
            fq.append("customer=").append(customerId);
        }
        SortSpec.addListContext(model, "/projects", fq.isEmpty() ? "" : "?" + fq, sp);
        return "projects/projects";
    }

    // ----- Create / edit -----

    @GetMapping("/new")
    public String newProject(Model model) {
        addFormContext(model, "create", null, null);
        model.addAttribute("form", ProjectForm.empty());
        return "projects/project-form";
    }

    @GetMapping("/{id}/edit")
    public String editProject(@PathVariable Long id, Model model, RedirectAttributes ra) {
        Project project = projectService.get(id);
        if (project == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("prj.notFound", null));
            return "redirect:/projects";
        }
        if (project.isCancelled()) {
            ra.addFlashAttribute("flashMessage", errorFlash("prj.notEditable", project.getName()));
            return "redirect:/projects/" + id;
        }
        addFormContext(model, "edit", id, project.getCustomerId());
        model.addAttribute("form", toForm(project));
        return "projects/project-form";
    }

    private ProjectForm toForm(Project project) {
        return new ProjectForm(project.getCode(), project.getName(), project.getCustomerId(),
                project.getDescription(), project.getBillingType().name(),
                project.getStartDate(), project.getEndDate(), project.getManager(),
                project.getFixedPrice(), project.getDefaultBillRate(), project.getDefaultCostRate(),
                project.getEstimatedHours(), project.getCurrencyCode(), project.getNotes(),
                Boolean.FALSE);
    }

    @PostMapping
    public String create(@ModelAttribute("form") ProjectForm form, BindingResult br,
                         Model model, RedirectAttributes ra) {
        validate(form, br, null);
        if (br.hasErrors()) {
            addFormContext(model, "create", null, form.customerId());
            return "projects/project-form";
        }
        try {
            Project saved = projectService.save(form, null, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("prj.saved", saved.getDisplayName()));
            return "redirect:/projects/" + saved.getId();
        } catch (RuntimeException ex) {
            rejectWithReason(br, ex);
            addFormContext(model, "create", null, form.customerId());
            return "projects/project-form";
        }
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute("form") ProjectForm form,
                         BindingResult br, Model model, RedirectAttributes ra) {
        if (projectService.get(id) == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("prj.notFound", null));
            return "redirect:/projects";
        }
        validate(form, br, id);
        if (br.hasErrors()) {
            addFormContext(model, "edit", id, form.customerId());
            return "projects/project-form";
        }
        try {
            Project saved = projectService.save(form, id, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("prj.saved", saved.getDisplayName()));
            return "redirect:/projects/" + saved.getId();
        } catch (RuntimeException ex) {
            rejectWithReason(br, ex);
            addFormContext(model, "edit", id, form.customerId());
            return "projects/project-form";
        }
    }

    private void validate(ProjectForm form, BindingResult br, Long excludeId) {
        if (form.name() == null || form.name().isBlank()) {
            br.rejectValue("name", "prj.nameRequired");
        } else if (projectService.nameExists(form.name(), excludeId)) {
            br.rejectValue("name", "prj.nameExists");
        }
        if (form.code() != null && !form.code().isBlank()
                && projectService.codeExists(form.code(), excludeId)) {
            br.rejectValue("code", "prj.codeExists");
        }
    }

    // ----- View -----

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model, RedirectAttributes ra) {
        Project project = projectService.get(id);
        if (project == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("prj.notFound", null));
            return "redirect:/projects";
        }
        model.addAttribute("project", project);
        model.addAttribute("position", projectService.positionOf(project));
        model.addAttribute("entries", timeEntryService.forProject(id));
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("billingLabels", billingLabels());
        model.addAttribute("timeStatusLabels", TimesheetController.timeStatusLabels(messageSource));
        model.addAttribute("baseCurrency", projectService.baseCurrency());
        return "projects/project-view";
    }

    // ----- Lifecycle -----

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id, RedirectAttributes ra) {
        return lifecycle(id, ra, "prj.activated", () -> projectService.activate(id));
    }

    @PostMapping("/{id}/hold")
    public String hold(@PathVariable Long id, RedirectAttributes ra) {
        return lifecycle(id, ra, "prj.held", () -> projectService.hold(id));
    }

    @PostMapping("/{id}/complete")
    public String complete(@PathVariable Long id, RedirectAttributes ra) {
        return lifecycle(id, ra, "prj.completed", () -> projectService.complete(id));
    }

    @PostMapping("/{id}/reopen")
    public String reopen(@PathVariable Long id, RedirectAttributes ra) {
        return lifecycle(id, ra, "prj.reopened", () -> projectService.reopen(id));
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id,
                         @RequestParam(value = "reason", required = false) String reason,
                         RedirectAttributes ra) {
        return lifecycle(id, ra, "prj.cancelled", () -> projectService.cancel(id, reason));
    }

    private String lifecycle(Long id, RedirectAttributes ra, String successKey,
                             java.util.function.Supplier<Project> action) {
        try {
            action.get();
            ra.addFlashAttribute("flashMessage", flash(successKey, null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("prj.actionFailed", ex.getMessage()));
        }
        return "redirect:/projects/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            projectService.delete(id);
            ra.addFlashAttribute("flashMessage", flash("prj.deleted", null));
            return "redirect:/projects";
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("prj.actionFailed", ex.getMessage()));
            return "redirect:/projects/" + id;
        }
    }
}
