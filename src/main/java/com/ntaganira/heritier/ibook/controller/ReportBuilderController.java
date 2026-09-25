/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : ReportBuilderController.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Report builder web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.ReportDefinitionForm;
import com.ntaganira.heritier.ibook.entity.ReportDefinition;
import com.ntaganira.heritier.ibook.enums.AccountType;
import com.ntaganira.heritier.ibook.enums.ReportBasis;
import com.ntaganira.heritier.ibook.enums.ReportComparison;
import com.ntaganira.heritier.ibook.enums.ReportRowType;
import com.ntaganira.heritier.ibook.service.AuditService;
import com.ntaganira.heritier.ibook.service.ReportBuilderService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/reports/builder")
public class ReportBuilderController {

    private static final int PAGE_SIZE = 20;

    private final ReportBuilderService reportBuilderService;
    private final MessageSource messageSource;

    public ReportBuilderController(ReportBuilderService reportBuilderService,
                                   MessageSource messageSource) {
        this.reportBuilderService = reportBuilderService;
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

    private Map<String, String> basisLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (ReportBasis b : ReportBasis.values()) {
            m.put(b.name(), msg("rbd.basis." + b.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private Map<String, String> comparisonLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (ReportComparison c : ReportComparison.values()) {
            m.put(c.name(), msg("rbd.comparison." + c.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private Map<String, String> rowTypeLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (ReportRowType r : ReportRowType.values()) {
            m.put(r.name(), msg("rbd.rowType." + r.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private Map<String, String> accountTypeLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (AccountType t : AccountType.values()) {
            m.put(t.name(), msg("common.type." + t.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private void addFormContext(Model model, String mode, Long editingId) {
        model.addAttribute("mode", mode);
        model.addAttribute("editingId", editingId);
        model.addAttribute("accounts", reportBuilderService.accounts());
        model.addAttribute("basisLabels", basisLabels());
        model.addAttribute("comparisonLabels", comparisonLabels());
        model.addAttribute("rowTypeLabels", rowTypeLabels());
        model.addAttribute("accountTypeLabels", accountTypeLabels());
    }

    // ----- List -----

    @GetMapping
    public String builder(@RequestParam(value = "q", required = false) String q,
                          @RequestParam(value = "basis", required = false) String basis,
                          @RequestParam(value = "sort", defaultValue = "name") String sort,
                          @RequestParam(value = "dir", defaultValue = "asc") String dir,
                          @RequestParam(value = "page", defaultValue = "0") int page,
                          Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "name", "name", "basis", "lastRunAt", "createdAt");
        model.addAttribute("definitions", reportBuilderService.list(q, basis,
                PageRequest.of(page, PAGE_SIZE, sp.sort())));
        model.addAttribute("summary", reportBuilderService.summary());
        model.addAttribute("basisLabels", basisLabels());
        model.addAttribute("comparisonLabels", comparisonLabels());
        model.addAttribute("q", q);
        model.addAttribute("basis", basis == null ? "" : basis);

        StringBuilder fq = new StringBuilder();
        if (q != null && !q.isBlank()) {
            fq.append("q=").append(UriUtils.encodeQueryParam(q, StandardCharsets.UTF_8));
        }
        if (basis != null && !basis.isBlank()) {
            if (!fq.isEmpty()) {
                fq.append('&');
            }
            fq.append("basis=").append(basis);
        }
        SortSpec.addListContext(model, "/reports/builder", fq.isEmpty() ? "" : "?" + fq, sp);
        return "reports/builder";
    }

    // ----- Form -----

    @GetMapping("/new")
    public String newDefinition(Model model) {
        addFormContext(model, "new", null);
        model.addAttribute("form", ReportDefinitionForm.empty());
        return "reports/builder-form";
    }

    @GetMapping("/{id}/edit")
    public String editDefinition(@PathVariable Long id, Model model, RedirectAttributes ra) {
        ReportDefinition definition = reportBuilderService.get(id);
        if (definition == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("rbd.notFound", null));
            return "redirect:/reports/builder";
        }
        addFormContext(model, "edit", id);
        model.addAttribute("form", reportBuilderService.toForm(definition));
        return "reports/builder-form";
    }

    @PostMapping
    public String create(@ModelAttribute("form") ReportDefinitionForm form, BindingResult br,
                         Model model, RedirectAttributes ra) {
        try {
            ReportDefinition saved = reportBuilderService.save(form, null,
                    AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("rbd.saved", saved.getName()));
            return "redirect:/reports/builder/" + saved.getId();
        } catch (RuntimeException ex) {
            String reason = ex.getMessage() == null ? msg("rbd.actionFailed") : ex.getMessage();
            br.reject("rbd.failedDetail", new Object[]{reason}, reason);
            addFormContext(model, "new", null);
            return "reports/builder-form";
        }
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute("form") ReportDefinitionForm form,
                         BindingResult br, Model model, RedirectAttributes ra) {
        if (reportBuilderService.get(id) == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("rbd.notFound", null));
            return "redirect:/reports/builder";
        }
        try {
            ReportDefinition saved = reportBuilderService.save(form, id,
                    AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("rbd.saved", saved.getName()));
            return "redirect:/reports/builder/" + saved.getId();
        } catch (RuntimeException ex) {
            String reason = ex.getMessage() == null ? msg("rbd.actionFailed") : ex.getMessage();
            br.reject("rbd.failedDetail", new Object[]{reason}, reason);
            addFormContext(model, "edit", id);
            return "reports/builder-form";
        }
    }

    // ----- Running -----

    @GetMapping("/{id}")
    public String run(@PathVariable Long id,
                      @RequestParam(value = "from", required = false)
                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                      @RequestParam(value = "to", required = false)
                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                      Model model, RedirectAttributes ra) {
        ReportDefinition definition = reportBuilderService.get(id);
        if (definition == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("rbd.notFound", null));
            return "redirect:/reports/builder";
        }
        LocalDate today = LocalDate.now();
        LocalDate periodFrom = from == null ? today.withDayOfYear(1) : from;
        LocalDate periodTo = to == null ? today : to;

        try {
            model.addAttribute("result", reportBuilderService.run(id, periodFrom, periodTo));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("rbd.actionFailed", ex.getMessage()));
            return "redirect:/reports/builder";
        }
        model.addAttribute("definition", definition);
        model.addAttribute("from", periodFrom);
        model.addAttribute("to", periodTo);
        model.addAttribute("basisLabels", basisLabels());
        model.addAttribute("comparisonLabels", comparisonLabels());
        return "reports/builder-run";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            reportBuilderService.delete(id);
            ra.addFlashAttribute("flashMessage", flash("rbd.deleted", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("rbd.actionFailed", ex.getMessage()));
        }
        return "redirect:/reports/builder";
    }
}
