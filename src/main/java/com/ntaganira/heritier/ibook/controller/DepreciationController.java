/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : DepreciationController.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Depreciation runs web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.DepreciationRunForm;
import com.ntaganira.heritier.ibook.entity.DepreciationRun;
import com.ntaganira.heritier.ibook.enums.DepreciationRunStatus;
import com.ntaganira.heritier.ibook.service.AuditService;
import com.ntaganira.heritier.ibook.service.DepreciationService;
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
@RequestMapping("/assets/depreciation")
public class DepreciationController {

    private static final int PAGE_SIZE = 20;

    private final DepreciationService depreciationService;
    private final MessageSource messageSource;

    public DepreciationController(DepreciationService depreciationService,
                                  MessageSource messageSource) {
        this.depreciationService = depreciationService;
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
        String reason = ex.getMessage() == null ? msg("ast.dep.actionFailed") : ex.getMessage();
        br.reject("ast.dep.failedDetail", new Object[]{reason}, reason);
    }

    private Map<String, String> statusLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (DepreciationRunStatus s : DepreciationRunStatus.values()) {
            m.put(s.name(), msg("ast.dep.status." + s.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    // ----- List -----

    @GetMapping
    public String runs(@RequestParam(value = "q", required = false) String q,
                       @RequestParam(value = "status", required = false) String status,
                       @RequestParam(value = "sort", defaultValue = "periodEnd") String sort,
                       @RequestParam(value = "dir", defaultValue = "desc") String dir,
                       @RequestParam(value = "page", defaultValue = "0") int page,
                       Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "periodEnd", "periodEnd", "runNo",
                "totalCharge", "assetCount", "status");
        model.addAttribute("runs", depreciationService.list(q, status,
                PageRequest.of(page, PAGE_SIZE, sp.sort())));
        model.addAttribute("summary", depreciationService.summary());
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("baseCurrency", depreciationService.baseCurrency());
        model.addAttribute("q", q);
        model.addAttribute("status", status == null ? "" : status);
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
        SortSpec.addListContext(model, "/assets/depreciation", fq.isEmpty() ? "" : "?" + fq, sp);
        return "assets/depreciation";
    }

    // ----- Prepare -----

    @GetMapping("/new")
    public String newRun(@RequestParam(value = "periodEnd", required = false)
                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd,
                         Model model) {
        LocalDate date = periodEnd == null ? LocalDate.now() : periodEnd;
        model.addAttribute("form", new DepreciationRunForm(date, null, Boolean.FALSE));
        addPreviewContext(model, date);
        return "assets/depreciation-form";
    }

    private void addPreviewContext(Model model, LocalDate periodEnd) {
        model.addAttribute("preview", depreciationService.preview(periodEnd));
        model.addAttribute("previewTotal", depreciationService.outstandingAt(periodEnd));
        model.addAttribute("periodEnd", periodEnd);
        model.addAttribute("baseCurrency", depreciationService.baseCurrency());
        model.addAttribute("nextNumber", depreciationService.previewNextNumber());
    }

    @PostMapping
    public String create(@ModelAttribute("form") DepreciationRunForm form, BindingResult br,
                         Model model, RedirectAttributes ra) {
        if (form.periodEnd() == null) {
            br.rejectValue("periodEnd", "ast.dep.dateRequired");
        }
        if (br.hasErrors()) {
            addPreviewContext(model, form.periodEnd() == null ? LocalDate.now() : form.periodEnd());
            return "assets/depreciation-form";
        }
        try {
            DepreciationRun saved = depreciationService.create(form, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("ast.dep.saved", saved.getRunNo()));
            return "redirect:/assets/depreciation/" + saved.getId();
        } catch (RuntimeException ex) {
            rejectWithReason(br, ex);
            addPreviewContext(model, form.periodEnd());
            return "assets/depreciation-form";
        }
    }

    // ----- View -----

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model, RedirectAttributes ra) {
        DepreciationRun run = depreciationService.get(id);
        if (run == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("ast.dep.notFound", null));
            return "redirect:/assets/depreciation";
        }
        model.addAttribute("run", run);
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("baseCurrency", depreciationService.baseCurrency());
        return "assets/depreciation-view";
    }

    // ----- Lifecycle -----

    @PostMapping("/{id}/post")
    public String post(@PathVariable Long id, RedirectAttributes ra) {
        try {
            DepreciationRun run = depreciationService.post(id, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("ast.dep.posted",
                    messageSource.getMessage("ast.dep.postedDetail",
                            new Object[]{run.getAssetCount(), run.getTotalCharge()},
                            LocaleContextHolder.getLocale())));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("ast.dep.actionFailed", ex.getMessage()));
        }
        return "redirect:/assets/depreciation/" + id;
    }

    @PostMapping("/{id}/void")
    public String voidRun(@PathVariable Long id,
                          @RequestParam(value = "reason", required = false) String reason,
                          RedirectAttributes ra) {
        try {
            depreciationService.voidRun(id, reason, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("ast.dep.voided", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("ast.dep.actionFailed", ex.getMessage()));
        }
        return "redirect:/assets/depreciation/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            depreciationService.delete(id);
            ra.addFlashAttribute("flashMessage", flash("ast.dep.deleted", null));
            return "redirect:/assets/depreciation";
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("ast.dep.actionFailed", ex.getMessage()));
            return "redirect:/assets/depreciation/" + id;
        }
    }
}
