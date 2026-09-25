/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : PayrollComponentController.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Allowances and deductions web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.PayrollComponentForm;
import com.ntaganira.heritier.ibook.entity.PayrollComponent;
import com.ntaganira.heritier.ibook.enums.PayrollComponentCalculation;
import com.ntaganira.heritier.ibook.enums.PayrollComponentKind;
import com.ntaganira.heritier.ibook.service.PayrollComponentService;
import jakarta.validation.Valid;
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

/**
 * Serves both {@code /payroll/allowances} and {@code /payroll/deductions}.
 *
 * <p>One controller because they are one list filtered by kind — the alternative is two of
 * everything that then drift apart. The kind travels through every route so a deduction saved from
 * the deductions page comes back to the deductions page.
 */
@Controller
@RequestMapping("/payroll")
public class PayrollComponentController {

    private static final int PAGE_SIZE = 20;

    private final PayrollComponentService payrollComponentService;
    private final MessageSource messageSource;

    public PayrollComponentController(PayrollComponentService payrollComponentService,
                                      MessageSource messageSource) {
        this.payrollComponentService = payrollComponentService;
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

    private static String pathOf(PayrollComponentKind kind) {
        return kind == PayrollComponentKind.DEDUCTION ? "/payroll/deductions" : "/payroll/allowances";
    }

    private Map<String, String> calculationLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (PayrollComponentCalculation c : PayrollComponentCalculation.values()) {
            m.put(c.name(), msg("pcm.calc." + c.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private void addKindContext(Model model, PayrollComponentKind kind) {
        model.addAttribute("kind", kind.name());
        model.addAttribute("basePathForKind", pathOf(kind));
        model.addAttribute("isDeduction", kind == PayrollComponentKind.DEDUCTION);
    }

    // ----- List -----

    @GetMapping("/allowances")
    public String allowances(@RequestParam(value = "q", required = false) String q,
                             @RequestParam(value = "active", required = false) String active,
                             @RequestParam(value = "sort", defaultValue = "sortOrder") String sort,
                             @RequestParam(value = "dir", defaultValue = "asc") String dir,
                             @RequestParam(value = "page", defaultValue = "0") int page,
                             Model model) {
        return listOf(PayrollComponentKind.ALLOWANCE, q, active, sort, dir, page, model);
    }

    @GetMapping("/deductions")
    public String deductions(@RequestParam(value = "q", required = false) String q,
                             @RequestParam(value = "active", required = false) String active,
                             @RequestParam(value = "sort", defaultValue = "sortOrder") String sort,
                             @RequestParam(value = "dir", defaultValue = "asc") String dir,
                             @RequestParam(value = "page", defaultValue = "0") int page,
                             Model model) {
        return listOf(PayrollComponentKind.DEDUCTION, q, active, sort, dir, page, model);
    }

    private String listOf(PayrollComponentKind kind, String q, String active,
                          String sort, String dir, int page, Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "sortOrder", "sortOrder", "code", "name", "amount");
        model.addAttribute("components", payrollComponentService.list(kind, q, active,
                PageRequest.of(page, PAGE_SIZE, sp.sort())));
        model.addAttribute("summary", payrollComponentService.summary(kind));
        model.addAttribute("calculationLabels", calculationLabels());
        model.addAttribute("q", q);
        model.addAttribute("active", active == null ? "" : active);
        addKindContext(model, kind);

        StringBuilder fq = new StringBuilder();
        if (q != null && !q.isBlank()) {
            fq.append("q=").append(UriUtils.encodeQueryParam(q, StandardCharsets.UTF_8));
        }
        if (active != null && !active.isBlank()) {
            if (!fq.isEmpty()) {
                fq.append('&');
            }
            fq.append("active=").append(active);
        }
        SortSpec.addListContext(model, pathOf(kind), fq.isEmpty() ? "" : "?" + fq, sp);
        return "payroll/components";
    }

    // ----- Form -----

    @GetMapping("/components/new")
    public String newComponent(@RequestParam(value = "kind", required = false) String kind,
                               Model model) {
        PayrollComponentKind resolved = PayrollComponentService.parseKind(kind);
        addKindContext(model, resolved);
        model.addAttribute("mode", "new");
        model.addAttribute("editingId", null);
        model.addAttribute("accounts", payrollComponentService.accounts());
        model.addAttribute("calculationLabels", calculationLabels());
        model.addAttribute("form", PayrollComponentForm.empty(resolved.name()));
        return "payroll/component-form";
    }

    @GetMapping("/components/{id}/edit")
    public String editComponent(@PathVariable Long id, Model model, RedirectAttributes ra) {
        PayrollComponent component = payrollComponentService.get(id);
        if (component == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("pcm.notFound", null));
            return "redirect:/payroll/allowances";
        }
        addKindContext(model, component.getKind());
        model.addAttribute("mode", "edit");
        model.addAttribute("editingId", id);
        model.addAttribute("accounts", payrollComponentService.accounts());
        model.addAttribute("calculationLabels", calculationLabels());
        model.addAttribute("usageCount", payrollComponentService.usageCount(id));
        model.addAttribute("form", payrollComponentService.toForm(component));
        return "payroll/component-form";
    }

    @PostMapping("/components")
    public String create(@Valid @ModelAttribute("form") PayrollComponentForm form,
                         BindingResult br, Model model, RedirectAttributes ra) {
        PayrollComponentKind kind = PayrollComponentService.parseKind(form.kind());
        if (!br.hasErrors()) {
            try {
                PayrollComponent saved = payrollComponentService.save(form, null);
                ra.addFlashAttribute("flashMessage", flash("pcm.saved", saved.getName()));
                return "redirect:" + pathOf(saved.getKind());
            } catch (RuntimeException ex) {
                String reason = ex.getMessage() == null ? msg("pcm.actionFailed") : ex.getMessage();
                br.reject("pcm.failedDetail", new Object[]{reason}, reason);
            }
        }
        addKindContext(model, kind);
        model.addAttribute("mode", "new");
        model.addAttribute("editingId", null);
        model.addAttribute("accounts", payrollComponentService.accounts());
        model.addAttribute("calculationLabels", calculationLabels());
        return "payroll/component-form";
    }

    @PostMapping("/components/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") PayrollComponentForm form,
                         BindingResult br, Model model, RedirectAttributes ra) {
        PayrollComponent existing = payrollComponentService.get(id);
        if (existing == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("pcm.notFound", null));
            return "redirect:/payroll/allowances";
        }
        PayrollComponentKind kind = PayrollComponentService.parseKind(form.kind());
        if (!br.hasErrors()) {
            try {
                PayrollComponent saved = payrollComponentService.save(form, id);
                ra.addFlashAttribute("flashMessage", flash("pcm.saved", saved.getName()));
                return "redirect:" + pathOf(saved.getKind());
            } catch (RuntimeException ex) {
                String reason = ex.getMessage() == null ? msg("pcm.actionFailed") : ex.getMessage();
                br.reject("pcm.failedDetail", new Object[]{reason}, reason);
            }
        }
        addKindContext(model, kind);
        model.addAttribute("mode", "edit");
        model.addAttribute("editingId", id);
        model.addAttribute("accounts", payrollComponentService.accounts());
        model.addAttribute("calculationLabels", calculationLabels());
        model.addAttribute("usageCount", payrollComponentService.usageCount(id));
        return "payroll/component-form";
    }

    // ----- Lifecycle -----

    @PostMapping("/components/{id}/active")
    public String setActive(@PathVariable Long id,
                            @RequestParam("active") boolean active,
                            RedirectAttributes ra) {
        PayrollComponent component = payrollComponentService.get(id);
        String back = component == null ? "/payroll/allowances" : pathOf(component.getKind());
        try {
            PayrollComponent saved = payrollComponentService.setActive(id, active);
            ra.addFlashAttribute("flashMessage",
                    flash(active ? "pcm.activated" : "pcm.deactivated", saved.getName()));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("pcm.actionFailed", ex.getMessage()));
        }
        return "redirect:" + back;
    }

    @PostMapping("/components/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        PayrollComponent component = payrollComponentService.get(id);
        String back = component == null ? "/payroll/allowances" : pathOf(component.getKind());
        try {
            payrollComponentService.delete(id);
            ra.addFlashAttribute("flashMessage", flash("pcm.deleted", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("pcm.actionFailed", ex.getMessage()));
        }
        return "redirect:" + back;
    }
}
