/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : ProgressBillingController.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Progress billing web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.ClaimInvoiceForm;
import com.ntaganira.heritier.ibook.dto.ProgressBillingForm;
import com.ntaganira.heritier.ibook.entity.ProgressBilling;
import com.ntaganira.heritier.ibook.entity.Project;
import com.ntaganira.heritier.ibook.enums.ProgressBillingMethod;
import com.ntaganira.heritier.ibook.enums.ProgressBillingStatus;
import com.ntaganira.heritier.ibook.service.AuditService;
import com.ntaganira.heritier.ibook.service.ProgressBillingService;
import com.ntaganira.heritier.ibook.service.ProjectService;
import com.ntaganira.heritier.ibook.service.TaxRateService;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/projects/progress-billing")
public class ProgressBillingController {

    private static final int PAGE_SIZE = 20;

    private final ProgressBillingService progressBillingService;
    private final ProjectService projectService;
    private final TaxRateService taxRateService;
    private final MessageSource messageSource;

    public ProgressBillingController(ProgressBillingService progressBillingService,
                                     ProjectService projectService,
                                     TaxRateService taxRateService,
                                     MessageSource messageSource) {
        this.progressBillingService = progressBillingService;
        this.projectService = projectService;
        this.taxRateService = taxRateService;
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
        String reason = ex.getMessage() == null ? msg("pgb.actionFailed") : ex.getMessage();
        br.reject("pgb.failedDetail", new Object[]{reason}, reason);
    }

    private Map<String, String> statusLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (ProgressBillingStatus s : ProgressBillingStatus.values()) {
            m.put(s.name(), msg("pgb.status." + s.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private Map<String, String> methodLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (ProgressBillingMethod v : ProgressBillingMethod.values()) {
            m.put(v.name(), msg("pgb.method." + v.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private Map<String, String> methodHints() {
        Map<String, String> m = new LinkedHashMap<>();
        for (ProgressBillingMethod v : ProgressBillingMethod.values()) {
            m.put(v.name(), msg("pgb.methodHint." + v.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    /** Where every claimable job stands, so the form is not the only place the figures exist. */
    private List<ProgressBillingService.Position> positions() {
        List<ProgressBillingService.Position> rows = new ArrayList<>();
        for (Project project : progressBillingService.claimableProjects(null)) {
            ProgressBillingService.Position position = progressBillingService.positionOf(project);
            if (position != null) {
                rows.add(position);
            }
        }
        return rows;
    }

    private void addFormContext(Model model, String mode, Long editingId, Long currentProjectId) {
        model.addAttribute("mode", mode);
        model.addAttribute("editingId", editingId);
        model.addAttribute("projects", progressBillingService.claimableProjects(currentProjectId));
        model.addAttribute("positions", positions());
        model.addAttribute("methodLabels", methodLabels());
        model.addAttribute("methodHints", methodHints());
        model.addAttribute("baseCurrency", projectService.baseCurrency());
    }

    // ----- List -----

    @GetMapping
    public String claims(@RequestParam(value = "q", required = false) String q,
                         @RequestParam(value = "status", required = false) String status,
                         @RequestParam(value = "project", required = false) Long projectId,
                         @RequestParam(value = "sort", defaultValue = "claimDate") String sort,
                         @RequestParam(value = "dir", defaultValue = "desc") String dir,
                         @RequestParam(value = "page", defaultValue = "0") int page,
                         Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "claimDate", "claimDate", "projectCode",
                "customerName", "netAmount", "status");
        model.addAttribute("claims", progressBillingService.list(q, status, projectId,
                PageRequest.of(page, PAGE_SIZE, sp.sort())));
        model.addAttribute("summary", progressBillingService.summary());
        model.addAttribute("positions", positions());
        model.addAttribute("projects", progressBillingService.claimableProjects(projectId));
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("methodLabels", methodLabels());
        model.addAttribute("baseCurrency", projectService.baseCurrency());
        model.addAttribute("q", q);
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("projectId", projectId);

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
        if (projectId != null) {
            if (!fq.isEmpty()) {
                fq.append('&');
            }
            fq.append("project=").append(projectId);
        }
        SortSpec.addListContext(model, "/projects/progress-billing",
                fq.isEmpty() ? "" : "?" + fq, sp);
        return "projects/progress-billing";
    }

    // ----- Create / edit -----

    @GetMapping("/new")
    public String newClaim(@RequestParam(value = "project", required = false) Long projectId,
                           Model model) {
        addFormContext(model, "create", null, projectId);
        model.addAttribute("form", new ProgressBillingForm(projectId, LocalDate.now(),
                "PERCENT_COMPLETE", null, null, null, null, null));
        return "projects/progress-billing-form";
    }

    @GetMapping("/{id}/edit")
    public String editClaim(@PathVariable Long id, Model model, RedirectAttributes ra) {
        ProgressBilling claim = progressBillingService.get(id);
        if (claim == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("pgb.notFound", null));
            return "redirect:/projects/progress-billing";
        }
        if (!claim.isEditable()) {
            ra.addFlashAttribute("flashMessage",
                    errorFlash("pgb.notEditable", claim.getClaimLabel()));
            return "redirect:/projects/progress-billing/" + id;
        }
        addFormContext(model, "edit", id, claim.getProjectId());
        model.addAttribute("form", toForm(claim));
        return "projects/progress-billing-form";
    }

    private ProgressBillingForm toForm(ProgressBilling claim) {
        return new ProgressBillingForm(claim.getProjectId(), claim.getClaimDate(),
                claim.getMethod().name(), claim.getPercentComplete(), claim.getGrossAmount(),
                claim.getRetentionPercent(), claim.getDescription(), claim.getNotes());
    }

    @PostMapping
    public String create(@ModelAttribute("form") ProgressBillingForm form, BindingResult br,
                         Model model, RedirectAttributes ra) {
        try {
            ProgressBilling saved = progressBillingService.save(form, null,
                    AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("pgb.saved", saved.getClaimLabel()));
            return "redirect:/projects/progress-billing/" + saved.getId();
        } catch (RuntimeException ex) {
            rejectWithReason(br, ex);
            addFormContext(model, "create", null, form.projectId());
            return "projects/progress-billing-form";
        }
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute("form") ProgressBillingForm form,
                         BindingResult br, Model model, RedirectAttributes ra) {
        if (progressBillingService.get(id) == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("pgb.notFound", null));
            return "redirect:/projects/progress-billing";
        }
        try {
            ProgressBilling saved = progressBillingService.save(form, id,
                    AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("pgb.saved", saved.getClaimLabel()));
            return "redirect:/projects/progress-billing/" + saved.getId();
        } catch (RuntimeException ex) {
            rejectWithReason(br, ex);
            addFormContext(model, "edit", id, form.projectId());
            return "projects/progress-billing-form";
        }
    }

    // ----- View -----

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model, RedirectAttributes ra) {
        ProgressBilling claim = progressBillingService.get(id);
        if (claim == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("pgb.notFound", null));
            return "redirect:/projects/progress-billing";
        }
        model.addAttribute("claim", claim);
        model.addAttribute("project", projectService.get(claim.getProjectId()));
        model.addAttribute("position",
                progressBillingService.positionOf(projectService.get(claim.getProjectId())));
        model.addAttribute("history", progressBillingService.forProject(claim.getProjectId()));
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("methodLabels", methodLabels());
        model.addAttribute("taxRates", taxRateService.listActive());
        model.addAttribute("revenueAccounts", progressBillingService.revenueAccounts());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("dueDefault", LocalDate.now().plusDays(30));
        model.addAttribute("baseCurrency", projectService.baseCurrency());
        return "projects/progress-billing-view";
    }

    // ----- Raise and lifecycle -----

    @PostMapping("/{id}/raise")
    public String raise(@PathVariable Long id, @ModelAttribute ClaimInvoiceForm form,
                        RedirectAttributes ra) {
        try {
            ProgressBilling saved = progressBillingService.raise(id, form,
                    AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("pgb.raised", saved.getInvoiceNo()));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("pgb.actionFailed", ex.getMessage()));
        }
        return "redirect:/projects/progress-billing/" + id;
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id,
                         @RequestParam(value = "reason", required = false) String reason,
                         RedirectAttributes ra) {
        try {
            progressBillingService.cancel(id, reason);
            ra.addFlashAttribute("flashMessage", flash("pgb.cancelled", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("pgb.actionFailed", ex.getMessage()));
        }
        return "redirect:/projects/progress-billing/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            progressBillingService.delete(id);
            ra.addFlashAttribute("flashMessage", flash("pgb.deleted", null));
            return "redirect:/projects/progress-billing";
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("pgb.actionFailed", ex.getMessage()));
            return "redirect:/projects/progress-billing/" + id;
        }
    }
}
