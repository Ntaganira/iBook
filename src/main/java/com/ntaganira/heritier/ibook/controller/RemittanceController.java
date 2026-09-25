/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : RemittanceController.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Payroll remittance web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.RemittanceForm;
import com.ntaganira.heritier.ibook.entity.Remittance;
import com.ntaganira.heritier.ibook.enums.RemittanceAuthority;
import com.ntaganira.heritier.ibook.enums.RemittanceStatus;
import com.ntaganira.heritier.ibook.service.AuditService;
import com.ntaganira.heritier.ibook.service.RemittanceService;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/payroll/remittances")
public class RemittanceController {

    private static final int PAGE_SIZE = 20;

    private final RemittanceService remittanceService;
    private final MessageSource messageSource;

    public RemittanceController(RemittanceService remittanceService, MessageSource messageSource) {
        this.remittanceService = remittanceService;
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

    private Map<String, String> authorityLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (RemittanceAuthority a : RemittanceAuthority.values()) {
            m.put(a.name(), msg("rem.authority." + a.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private Map<String, String> statusLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (RemittanceStatus s : RemittanceStatus.values()) {
            m.put(s.name(), msg("rem.status." + s.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private void addFormContext(Model model, String mode, Long editingId) {
        model.addAttribute("mode", mode);
        model.addAttribute("editingId", editingId);
        model.addAttribute("paymentAccounts", remittanceService.paymentAccounts());
        model.addAttribute("authorityLabels", authorityLabels());
    }

    // ----- List -----

    @GetMapping
    public String remittances(@RequestParam(value = "q", required = false) String q,
                              @RequestParam(value = "authority", required = false) String authority,
                              @RequestParam(value = "status", required = false) String status,
                              @RequestParam(value = "from", required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                              @RequestParam(value = "to", required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                              @RequestParam(value = "sort", defaultValue = "paymentDate") String sort,
                              @RequestParam(value = "dir", defaultValue = "desc") String dir,
                              @RequestParam(value = "page", defaultValue = "0") int page,
                              Model model) {
        LocalDate today = LocalDate.now();
        LocalDate lastMonth = today.minusMonths(1);
        LocalDate periodFrom = from == null ? lastMonth.withDayOfMonth(1) : from;
        LocalDate periodTo = to == null
                ? lastMonth.withDayOfMonth(lastMonth.lengthOfMonth()) : to;

        SortSpec sp = SortSpec.resolve(sort, dir, "paymentDate", "paymentDate", "reference",
                "authority", "amount", "status");
        model.addAttribute("remittances", remittanceService.list(q, authority, status,
                PageRequest.of(page, PAGE_SIZE, sp.sort())));
        model.addAttribute("positions", remittanceService.positions(periodFrom, periodTo));
        model.addAttribute("summary", remittanceService.summary());
        model.addAttribute("authorityLabels", authorityLabels());
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("q", q);
        model.addAttribute("authority", authority == null ? "" : authority);
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("from", periodFrom);
        model.addAttribute("to", periodTo);

        StringBuilder fq = new StringBuilder();
        fq.append("from=").append(periodFrom).append("&to=").append(periodTo);
        if (authority != null && !authority.isBlank()) {
            fq.append("&authority=").append(authority);
        }
        if (status != null && !status.isBlank()) {
            fq.append("&status=").append(status);
        }
        SortSpec.addListContext(model, "/payroll/remittances", "?" + fq, sp);
        return "payroll/remittances";
    }

    // ----- Form -----

    @GetMapping("/new")
    public String newRemittance(@RequestParam(value = "authority", required = false) String authority,
                                @RequestParam(value = "from", required = false)
                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam(value = "to", required = false)
                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                Model model) {
        RemittanceForm form = RemittanceForm.empty(authority);
        RemittanceAuthority resolved = RemittanceService.parseAuthority(authority);
        if (resolved != null && from != null && to != null) {
            // Prefilled with what the posted runs owe, which is the figure being declared in the
            // ordinary case — still editable, because a declaration can differ from the ledger.
            form = new RemittanceForm(resolved.name(), from, to, LocalDate.now(),
                    remittanceService.expectedFor(resolved, from, to), null, null, null, false);
        }
        addFormContext(model, "new", null);
        model.addAttribute("form", form);
        return "payroll/remittance-form";
    }

    @GetMapping("/{id}/edit")
    public String editRemittance(@PathVariable Long id, Model model, RedirectAttributes ra) {
        Remittance remittance = remittanceService.get(id);
        if (remittance == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("rem.notFound", null));
            return "redirect:/payroll/remittances";
        }
        if (!remittance.isEditable()) {
            ra.addFlashAttribute("flashMessage", errorFlash("rem.notEditable", remittance.getReference()));
            return "redirect:/payroll/remittances/" + id;
        }
        addFormContext(model, "edit", id);
        model.addAttribute("form", remittanceService.toForm(remittance));
        return "payroll/remittance-form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") RemittanceForm form, BindingResult br,
                         Model model, RedirectAttributes ra) {
        if (!br.hasErrors()) {
            try {
                Remittance saved = remittanceService.save(form, null, AuditService.currentUsername());
                ra.addFlashAttribute("flashMessage", flash("rem.saved", saved.getReference()));
                return "redirect:/payroll/remittances/" + saved.getId();
            } catch (RuntimeException ex) {
                String reason = ex.getMessage() == null ? msg("rem.actionFailed") : ex.getMessage();
                br.reject("rem.failedDetail", new Object[]{reason}, reason);
            }
        }
        addFormContext(model, "new", null);
        return "payroll/remittance-form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") RemittanceForm form,
                         BindingResult br, Model model, RedirectAttributes ra) {
        if (remittanceService.get(id) == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("rem.notFound", null));
            return "redirect:/payroll/remittances";
        }
        if (!br.hasErrors()) {
            try {
                Remittance saved = remittanceService.save(form, id, AuditService.currentUsername());
                ra.addFlashAttribute("flashMessage", flash("rem.saved", saved.getReference()));
                return "redirect:/payroll/remittances/" + saved.getId();
            } catch (RuntimeException ex) {
                String reason = ex.getMessage() == null ? msg("rem.actionFailed") : ex.getMessage();
                br.reject("rem.failedDetail", new Object[]{reason}, reason);
            }
        }
        addFormContext(model, "edit", id);
        return "payroll/remittance-form";
    }

    // ----- Detail -----

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model, RedirectAttributes ra) {
        Remittance remittance = remittanceService.get(id);
        if (remittance == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("rem.notFound", null));
            return "redirect:/payroll/remittances";
        }
        model.addAttribute("remittance", remittance);
        model.addAttribute("journal", remittanceService.journalFor(remittance));
        model.addAttribute("authorityLabels", authorityLabels());
        model.addAttribute("statusLabels", statusLabels());
        return "payroll/remittance-view";
    }

    // ----- Lifecycle -----

    @PostMapping("/{id}/pay")
    public String pay(@PathVariable Long id, RedirectAttributes ra) {
        try {
            Remittance saved = remittanceService.pay(id, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("rem.paid", saved.getReference()));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("rem.actionFailed", ex.getMessage()));
        }
        return "redirect:/payroll/remittances/" + id;
    }

    @PostMapping("/{id}/void")
    public String voidRemittance(@PathVariable Long id,
                                 @RequestParam(value = "reason", required = false) String reason,
                                 RedirectAttributes ra) {
        try {
            Remittance saved = remittanceService.voidRemittance(id, reason,
                    AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("rem.voided", saved.getReference()));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("rem.actionFailed", ex.getMessage()));
        }
        return "redirect:/payroll/remittances/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            remittanceService.delete(id);
            ra.addFlashAttribute("flashMessage", flash("rem.deleted", null));
            return "redirect:/payroll/remittances";
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("rem.actionFailed", ex.getMessage()));
            return "redirect:/payroll/remittances/" + id;
        }
    }
}
