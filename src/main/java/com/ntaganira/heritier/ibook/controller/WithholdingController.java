/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : WithholdingController.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : Withholding tax web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.WithholdingCertificateForm;
import com.ntaganira.heritier.ibook.dto.WithholdingSettingsForm;
import com.ntaganira.heritier.ibook.entity.WithholdingCertificate;
import com.ntaganira.heritier.ibook.entity.WithholdingSettings;
import com.ntaganira.heritier.ibook.enums.WithholdingStatus;
import com.ntaganira.heritier.ibook.service.AuditService;
import com.ntaganira.heritier.ibook.service.WithholdingService;
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
import org.springframework.web.util.UriUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Serves both halves of withholding: the tax view at {@code /taxes/withholding} — the rate table,
 * the period return and the remittance — and the operational view at
 * {@code /purchases/withholding}, which is the register of certificates and the place they are
 * raised from.
 */
@Controller
public class WithholdingController {

    private static final int PAGE_SIZE = 20;

    private final WithholdingService withholdingService;
    private final MessageSource messageSource;

    public WithholdingController(WithholdingService withholdingService,
                                 MessageSource messageSource) {
        this.withholdingService = withholdingService;
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
        for (WithholdingStatus s : WithholdingStatus.values()) {
            m.put(s.name(), msg("wht.status." + s.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    // ----- The tax view -----

    @GetMapping("/taxes/withholding")
    public String taxView(@RequestParam(value = "from", required = false)
                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(value = "to", required = false)
                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          Model model) {
        LocalDate today = LocalDate.now();
        LocalDate periodFrom = from == null ? today.withDayOfMonth(1) : from;
        LocalDate periodTo = to == null ? today.withDayOfMonth(today.lengthOfMonth()) : to;

        WithholdingSettings settings = withholdingService.settings();
        model.addAttribute("settings", settings);
        model.addAttribute("form", withholdingService.toForm(settings));
        model.addAttribute("accounts", withholdingService.liabilityAccounts());
        model.addAttribute("paymentAccounts", withholdingService.paymentAccounts());
        model.addAttribute("missingAccounts", withholdingService.missingExpectedAccounts());
        model.addAttribute("summary", withholdingService.summary(periodFrom, periodTo));
        model.addAttribute("byVendor", withholdingService.byVendor(periodFrom, periodTo));
        model.addAttribute("from", periodFrom);
        model.addAttribute("today", today);
        model.addAttribute("to", periodTo);
        return "taxes/withholding";
    }

    @PostMapping("/taxes/withholding")
    public String saveSettings(@ModelAttribute("form") WithholdingSettingsForm form,
                               BindingResult br, Model model, RedirectAttributes ra) {
        try {
            withholdingService.saveSettings(form);
            ra.addFlashAttribute("flashMessage", flash("wht.saved", msg("wht.confirmWithdrawn")));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("wht.actionFailed", ex.getMessage()));
        }
        return "redirect:/taxes/withholding";
    }

    @PostMapping("/taxes/withholding/confirm")
    public String confirm(@RequestParam(value = "rateSource", required = false) String rateSource,
                          RedirectAttributes ra) {
        try {
            withholdingService.confirm(rateSource, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("wht.confirmed", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("wht.actionFailed", ex.getMessage()));
        }
        return "redirect:/taxes/withholding";
    }

    @PostMapping("/taxes/withholding/withdraw")
    public String withdraw(RedirectAttributes ra) {
        try {
            withholdingService.withdrawConfirmation(AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("wht.withdrawn", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("wht.actionFailed", ex.getMessage()));
        }
        return "redirect:/taxes/withholding";
    }

    @PostMapping("/taxes/withholding/remit")
    public String remit(@RequestParam("paymentDate")
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paymentDate,
                        @RequestParam("amount") BigDecimal amount,
                        @RequestParam("paymentAccountId") Long paymentAccountId,
                        @RequestParam(value = "declarationNo", required = false) String declarationNo,
                        RedirectAttributes ra) {
        try {
            withholdingService.remit(paymentDate, amount, paymentAccountId, declarationNo,
                    AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("wht.remitted", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("wht.actionFailed", ex.getMessage()));
        }
        return "redirect:/taxes/withholding";
    }

    // ----- The operational view -----

    @GetMapping("/purchases/withholding")
    public String register(@RequestParam(value = "q", required = false) String q,
                           @RequestParam(value = "vendorId", required = false) Long vendorId,
                           @RequestParam(value = "status", required = false) String status,
                           @RequestParam(value = "sort", defaultValue = "certificateDate") String sort,
                           @RequestParam(value = "dir", defaultValue = "desc") String dir,
                           @RequestParam(value = "page", defaultValue = "0") int page,
                           Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "certificateDate", "certificateDate",
                "certificateNo", "vendorName", "amount", "status");
        LocalDate today = LocalDate.now();
        model.addAttribute("certificates", withholdingService.list(q, vendorId, status,
                PageRequest.of(page, PAGE_SIZE, sp.sort())));
        model.addAttribute("summary", withholdingService.summary(today.withDayOfYear(1), today));
        model.addAttribute("vendors", withholdingService.vendors());
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("q", q);
        model.addAttribute("vendorId", vendorId);
        model.addAttribute("status", status == null ? "" : status);

        StringBuilder fq = new StringBuilder();
        if (q != null && !q.isBlank()) {
            fq.append("q=").append(UriUtils.encodeQueryParam(q, StandardCharsets.UTF_8));
        }
        if (vendorId != null) {
            if (!fq.isEmpty()) {
                fq.append('&');
            }
            fq.append("vendorId=").append(vendorId);
        }
        if (status != null && !status.isBlank()) {
            if (!fq.isEmpty()) {
                fq.append('&');
            }
            fq.append("status=").append(status);
        }
        SortSpec.addListContext(model, "/purchases/withholding",
                fq.isEmpty() ? "" : "?" + fq, sp);
        return "purchases/withholding";
    }

    private void addFormContext(Model model, String mode, Long editingId) {
        model.addAttribute("mode", mode);
        model.addAttribute("editingId", editingId);
        model.addAttribute("bills", withholdingService.withholdableBills());
        model.addAttribute("rates", withholdingService.rates());
        model.addAttribute("settings", withholdingService.settingsReadOnly());
    }

    @GetMapping("/purchases/withholding/new")
    public String newCertificate(Model model) {
        addFormContext(model, "new", null);
        model.addAttribute("form", new WithholdingCertificateForm(null, null, LocalDate.now(),
                BigDecimal.ZERO, null, Boolean.FALSE));
        return "purchases/withholding-form";
    }

    @GetMapping("/purchases/withholding/{id}/edit")
    public String editCertificate(@PathVariable Long id, Model model, RedirectAttributes ra) {
        WithholdingCertificate certificate = withholdingService.get(id);
        if (certificate == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("wht.notFound", null));
            return "redirect:/purchases/withholding";
        }
        if (!certificate.isEditable()) {
            ra.addFlashAttribute("flashMessage",
                    errorFlash("wht.notEditable", certificate.getCertificateNo()));
            return "redirect:/purchases/withholding/" + id;
        }
        addFormContext(model, "edit", id);
        model.addAttribute("form", withholdingService.toForm(certificate));
        return "purchases/withholding-form";
    }

    @PostMapping("/purchases/withholding")
    public String create(@Valid @ModelAttribute("form") WithholdingCertificateForm form,
                         BindingResult br, Model model, RedirectAttributes ra) {
        if (!br.hasErrors()) {
            try {
                WithholdingCertificate saved = withholdingService.save(form, null,
                        AuditService.currentUsername());
                ra.addFlashAttribute("flashMessage", flash("wht.certSaved", saved.getCertificateNo()));
                return "redirect:/purchases/withholding/" + saved.getId();
            } catch (RuntimeException ex) {
                String reason = ex.getMessage() == null ? msg("wht.actionFailed") : ex.getMessage();
                br.reject("wht.failedDetail", new Object[]{reason}, reason);
            }
        }
        addFormContext(model, "new", null);
        return "purchases/withholding-form";
    }

    @PostMapping("/purchases/withholding/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") WithholdingCertificateForm form,
                         BindingResult br, Model model, RedirectAttributes ra) {
        if (withholdingService.get(id) == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("wht.notFound", null));
            return "redirect:/purchases/withholding";
        }
        if (!br.hasErrors()) {
            try {
                WithholdingCertificate saved = withholdingService.save(form, id,
                        AuditService.currentUsername());
                ra.addFlashAttribute("flashMessage", flash("wht.certSaved", saved.getCertificateNo()));
                return "redirect:/purchases/withholding/" + saved.getId();
            } catch (RuntimeException ex) {
                String reason = ex.getMessage() == null ? msg("wht.actionFailed") : ex.getMessage();
                br.reject("wht.failedDetail", new Object[]{reason}, reason);
            }
        }
        addFormContext(model, "edit", id);
        return "purchases/withholding-form";
    }

    @GetMapping("/purchases/withholding/{id}")
    public String view(@PathVariable Long id, Model model, RedirectAttributes ra) {
        WithholdingCertificate certificate = withholdingService.get(id);
        if (certificate == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("wht.notFound", null));
            return "redirect:/purchases/withholding";
        }
        model.addAttribute("certificate", certificate);
        model.addAttribute("journal", withholdingService.journalFor(certificate));
        model.addAttribute("statusLabels", statusLabels());
        return "purchases/withholding-view";
    }

    @PostMapping("/purchases/withholding/{id}/issue")
    public String issue(@PathVariable Long id, RedirectAttributes ra) {
        try {
            WithholdingCertificate saved = withholdingService.issue(id,
                    AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("wht.issued", saved.getCertificateNo()));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("wht.actionFailed", ex.getMessage()));
        }
        return "redirect:/purchases/withholding/" + id;
    }

    @PostMapping("/purchases/withholding/{id}/void")
    public String voidIt(@PathVariable Long id,
                         @RequestParam(value = "reason", required = false) String reason,
                         RedirectAttributes ra) {
        try {
            withholdingService.voidCertificate(id, reason, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("wht.voided", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("wht.actionFailed", ex.getMessage()));
        }
        return "redirect:/purchases/withholding/" + id;
    }

    @PostMapping("/purchases/withholding/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            withholdingService.delete(id);
            ra.addFlashAttribute("flashMessage", flash("wht.deleted", null));
            return "redirect:/purchases/withholding";
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("wht.actionFailed", ex.getMessage()));
            return "redirect:/purchases/withholding/" + id;
        }
    }
}
