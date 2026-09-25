/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : BankReconciliationController.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Bank reconciliation web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.BankReconciliationForm;
import com.ntaganira.heritier.ibook.entity.BankReconciliation;
import com.ntaganira.heritier.ibook.enums.ReconciliationStatus;
import com.ntaganira.heritier.ibook.service.AuditService;
import com.ntaganira.heritier.ibook.service.BankReconciliationService;
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
@RequestMapping("/banking/reconcile")
public class BankReconciliationController {

    private static final int PAGE_SIZE = 20;

    private final BankReconciliationService bankReconciliationService;
    private final MessageSource messageSource;

    public BankReconciliationController(BankReconciliationService bankReconciliationService,
                                        MessageSource messageSource) {
        this.bankReconciliationService = bankReconciliationService;
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

    private Map<String, String> statusLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (ReconciliationStatus s : ReconciliationStatus.values()) {
            m.put(s.name(), msg("bnr.status." + s.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    // ----- List -----

    @GetMapping
    public String reconciliations(@RequestParam(value = "q", required = false) String q,
                                  @RequestParam(value = "accountId", required = false) Long accountId,
                                  @RequestParam(value = "status", required = false) String status,
                                  @RequestParam(value = "sort", defaultValue = "statementDate") String sort,
                                  @RequestParam(value = "dir", defaultValue = "desc") String dir,
                                  @RequestParam(value = "page", defaultValue = "0") int page,
                                  Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "statementDate", "statementDate", "reference",
                "accountCode", "closingBalance", "status");
        model.addAttribute("reconciliations", bankReconciliationService.list(q, accountId, status,
                PageRequest.of(page, PAGE_SIZE, sp.sort())));
        model.addAttribute("summary", bankReconciliationService.summary());
        model.addAttribute("bankAccounts", bankReconciliationService.bankAccounts());
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("q", q);
        model.addAttribute("accountId", accountId);
        model.addAttribute("status", status == null ? "" : status);

        StringBuilder fq = new StringBuilder();
        if (q != null && !q.isBlank()) {
            fq.append("q=").append(UriUtils.encodeQueryParam(q, StandardCharsets.UTF_8));
        }
        if (accountId != null) {
            if (!fq.isEmpty()) {
                fq.append('&');
            }
            fq.append("accountId=").append(accountId);
        }
        if (status != null && !status.isBlank()) {
            if (!fq.isEmpty()) {
                fq.append('&');
            }
            fq.append("status=").append(status);
        }
        SortSpec.addListContext(model, "/banking/reconcile", fq.isEmpty() ? "" : "?" + fq, sp);
        return "banking/reconcile";
    }

    // ----- Form -----

    @GetMapping("/new")
    public String newReconciliation(Model model) {
        model.addAttribute("mode", "new");
        model.addAttribute("editingId", null);
        model.addAttribute("bankAccounts", bankReconciliationService.bankAccounts());
        model.addAttribute("form", BankReconciliationForm.empty());
        return "banking/reconcile-form";
    }

    @GetMapping("/{id}/edit")
    public String editReconciliation(@PathVariable Long id, Model model, RedirectAttributes ra) {
        BankReconciliation reconciliation = bankReconciliationService.get(id);
        if (reconciliation == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("bnr.notFound", null));
            return "redirect:/banking/reconcile";
        }
        if (!reconciliation.isEditable()) {
            ra.addFlashAttribute("flashMessage", errorFlash("bnr.notEditable", reconciliation.getReference()));
            return "redirect:/banking/reconcile/" + id;
        }
        model.addAttribute("mode", "edit");
        model.addAttribute("editingId", id);
        model.addAttribute("bankAccounts", bankReconciliationService.bankAccounts());
        model.addAttribute("form", bankReconciliationService.toForm(reconciliation));
        return "banking/reconcile-form";
    }

    @PostMapping
    public String create(@ModelAttribute("form") BankReconciliationForm form, BindingResult br,
                         Model model, RedirectAttributes ra) {
        try {
            BankReconciliation saved = bankReconciliationService.save(form, null,
                    AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("bnr.saved", saved.getReference()));
            return "redirect:/banking/reconcile/" + saved.getId();
        } catch (RuntimeException ex) {
            String reason = ex.getMessage() == null ? msg("bnr.actionFailed") : ex.getMessage();
            br.reject("bnr.failedDetail", new Object[]{reason}, reason);
            model.addAttribute("mode", "new");
            model.addAttribute("editingId", null);
            model.addAttribute("bankAccounts", bankReconciliationService.bankAccounts());
            return "banking/reconcile-form";
        }
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @ModelAttribute("form") BankReconciliationForm form,
                         BindingResult br, Model model, RedirectAttributes ra) {
        if (bankReconciliationService.get(id) == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("bnr.notFound", null));
            return "redirect:/banking/reconcile";
        }
        try {
            BankReconciliation saved = bankReconciliationService.save(form, id,
                    AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("bnr.saved", saved.getReference()));
            return "redirect:/banking/reconcile/" + saved.getId();
        } catch (RuntimeException ex) {
            String reason = ex.getMessage() == null ? msg("bnr.actionFailed") : ex.getMessage();
            br.reject("bnr.failedDetail", new Object[]{reason}, reason);
            model.addAttribute("mode", "edit");
            model.addAttribute("editingId", id);
            model.addAttribute("bankAccounts", bankReconciliationService.bankAccounts());
            return "banking/reconcile-form";
        }
    }

    // ----- Matching -----

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model, RedirectAttributes ra) {
        BankReconciliation reconciliation = bankReconciliationService.get(id);
        if (reconciliation == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("bnr.notFound", null));
            return "redirect:/banking/reconcile";
        }
        model.addAttribute("reconciliation", reconciliation);
        model.addAttribute("position", bankReconciliationService.position(reconciliation));
        model.addAttribute("statusLabels", statusLabels());
        return "banking/reconcile-view";
    }

    @PostMapping("/{id}/match")
    public String match(@PathVariable Long id,
                        @RequestParam("statementLineId") Long statementLineId,
                        @RequestParam(value = "journalLineId", required = false) Long journalLineId,
                        RedirectAttributes ra) {
        try {
            bankReconciliationService.match(id, statementLineId, journalLineId);
            ra.addFlashAttribute("flashMessage",
                    flash(journalLineId == null ? "bnr.unmatched" : "bnr.matched", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("bnr.actionFailed", ex.getMessage()));
        }
        return "redirect:/banking/reconcile/" + id;
    }

    // ----- Lifecycle -----

    @PostMapping("/{id}/complete")
    public String complete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            BankReconciliation saved = bankReconciliationService.complete(id,
                    AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("bnr.completed", saved.getReference()));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("bnr.actionFailed", ex.getMessage()));
        }
        return "redirect:/banking/reconcile/" + id;
    }

    @PostMapping("/{id}/reopen")
    public String reopen(@PathVariable Long id, RedirectAttributes ra) {
        try {
            bankReconciliationService.reopen(id);
            ra.addFlashAttribute("flashMessage", flash("bnr.reopened", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("bnr.actionFailed", ex.getMessage()));
        }
        return "redirect:/banking/reconcile/" + id;
    }

    @PostMapping("/{id}/void")
    public String voidIt(@PathVariable Long id,
                         @RequestParam(value = "reason", required = false) String reason,
                         RedirectAttributes ra) {
        try {
            bankReconciliationService.voidReconciliation(id, reason);
            ra.addFlashAttribute("flashMessage", flash("bnr.voided", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("bnr.actionFailed", ex.getMessage()));
        }
        return "redirect:/banking/reconcile/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            bankReconciliationService.delete(id);
            ra.addFlashAttribute("flashMessage", flash("bnr.deleted", null));
            return "redirect:/banking/reconcile";
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("bnr.actionFailed", ex.getMessage()));
            return "redirect:/banking/reconcile/" + id;
        }
    }
}
