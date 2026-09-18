/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : TaxFilingController.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Tax filing log web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.TaxFilingForm;
import com.ntaganira.heritier.ibook.entity.Company;
import com.ntaganira.heritier.ibook.entity.TaxFiling;
import com.ntaganira.heritier.ibook.enums.FilingStatus;
import com.ntaganira.heritier.ibook.enums.TaxFilingType;
import com.ntaganira.heritier.ibook.repository.CompanyRepository;
import com.ntaganira.heritier.ibook.service.AuditService;
import com.ntaganira.heritier.ibook.service.TaxFilingService;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/taxes/filings")
public class TaxFilingController {

    private static final int PAGE_SIZE = 20;

    private final TaxFilingService filingService;
    private final CompanyRepository companyRepository;
    private final MessageSource messageSource;

    public TaxFilingController(TaxFilingService filingService,
                               CompanyRepository companyRepository,
                               MessageSource messageSource) {
        this.filingService = filingService;
        this.companyRepository = companyRepository;
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

    private String baseCurrency() {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElse(null);
        return company != null && company.getCurrencyCode() != null ? company.getCurrencyCode() : "RWF";
    }

    private Map<String, String> typeLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        for (TaxFilingType t : TaxFilingType.values()) {
            labels.put(t.name(), msg("tax.fil.type." + t.name().toLowerCase(Locale.ROOT)));
        }
        return labels;
    }

    private Map<String, String> statusLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        for (FilingStatus s : FilingStatus.values()) {
            labels.put(s.name(), msg("tax.fil.status." + s.name().toLowerCase(Locale.ROOT)));
        }
        return labels;
    }

    private void addFormContext(Model model, String mode, Long editingId) {
        model.addAttribute("mode", mode);
        model.addAttribute("editingId", editingId);
        model.addAttribute("typeLabels", typeLabels());
        model.addAttribute("baseCurrency", baseCurrency());
    }

    // ----- List -----

    @GetMapping
    public String filings(@RequestParam(value = "type", required = false) String type,
                          @RequestParam(value = "status", required = false) String status,
                          @RequestParam(value = "sort", defaultValue = "periodTo") String sort,
                          @RequestParam(value = "dir", defaultValue = "desc") String dir,
                          @RequestParam(value = "page", defaultValue = "0") int page,
                          Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "periodTo", "periodTo", "periodFrom", "dueDate",
                "filingType", "status", "declaredAmount");
        Page<TaxFiling> result = filingService.list(type, status, PageRequest.of(page, PAGE_SIZE, sp.sort()));
        model.addAttribute("filings", result);
        model.addAttribute("summary", filingService.summary());
        model.addAttribute("typeLabels", typeLabels());
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("baseCurrency", baseCurrency());
        model.addAttribute("type", type == null ? "" : type);
        model.addAttribute("status", status == null ? "" : status);
        StringBuilder fq = new StringBuilder();
        if (type != null && !type.isBlank()) {
            fq.append("type=").append(type);
        }
        if (status != null && !status.isBlank()) {
            if (!fq.isEmpty()) {
                fq.append('&');
            }
            fq.append("status=").append(status);
        }
        SortSpec.addListContext(model, "/taxes/filings", fq.isEmpty() ? "" : "?" + fq, sp);
        return "taxes/filings";
    }

    // ----- Create / edit -----

    @GetMapping("/new")
    public String newFiling(@RequestParam(value = "type", required = false) String type,
                            @RequestParam(value = "from", required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(value = "to", required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            Model model) {
        LocalDate periodFrom = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate periodTo = to != null ? to
                : periodFrom.withDayOfMonth(periodFrom.lengthOfMonth());
        TaxFilingType filingType;
        try {
            filingType = type == null || type.isBlank()
                    ? TaxFilingType.VAT
                    : TaxFilingType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            filingType = TaxFilingType.VAT;
        }
        BigDecimal suggested = filingService.suggestedAmount(filingType, periodFrom, periodTo);
        addFormContext(model, "create", null);
        model.addAttribute("suggested", suggested);
        model.addAttribute("form", new TaxFilingForm(filingType.name(), periodFrom, periodTo,
                TaxFilingService.defaultDueDate(filingType, periodTo), suggested,
                BigDecimal.ZERO, null, null));
        return "taxes/filing-form";
    }

    @GetMapping("/{id}/edit")
    public String editFiling(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        TaxFiling f = filingService.get(id);
        if (f == null) {
            redirectAttributes.addFlashAttribute("flashMessage", errorFlash("tax.fil.notFound", null));
            return "redirect:/taxes/filings";
        }
        if (!f.isEditable()) {
            redirectAttributes.addFlashAttribute("flashMessage",
                    errorFlash("tax.fil.onlyDraftEditable", null));
            return "redirect:/taxes/filings";
        }
        addFormContext(model, "edit", id);
        model.addAttribute("suggested", filingService.suggestedAmount(
                f.getFilingType(), f.getPeriodFrom(), f.getPeriodTo()));
        model.addAttribute("form", new TaxFilingForm(f.getFilingType().name(), f.getPeriodFrom(),
                f.getPeriodTo(), f.getDueDate(), f.getDeclaredAmount(), f.getPaidAmount(),
                f.getReference(), f.getNotes()));
        return "taxes/filing-form";
    }

    @PostMapping
    public String createFiling(@Valid @ModelAttribute("form") TaxFilingForm form,
                               BindingResult bindingResult, Model model,
                               RedirectAttributes redirectAttributes) {
        validate(form, bindingResult, null);
        if (bindingResult.hasErrors()) {
            addFormContext(model, "create", null);
            model.addAttribute("suggested", BigDecimal.ZERO);
            return "taxes/filing-form";
        }
        TaxFiling saved = filingService.save(form, null, AuditService.currentUsername());
        redirectAttributes.addFlashAttribute("flashMessage", flash("tax.fil.saved", saved.getPeriodLabel()));
        return "redirect:/taxes/filings";
    }

    @PostMapping("/{id}")
    public String updateFiling(@PathVariable Long id, @Valid @ModelAttribute("form") TaxFilingForm form,
                               BindingResult bindingResult, Model model,
                               RedirectAttributes redirectAttributes) {
        validate(form, bindingResult, id);
        if (bindingResult.hasErrors()) {
            addFormContext(model, "edit", id);
            model.addAttribute("suggested", BigDecimal.ZERO);
            return "taxes/filing-form";
        }
        try {
            TaxFiling saved = filingService.save(form, id, AuditService.currentUsername());
            redirectAttributes.addFlashAttribute("flashMessage", flash("tax.fil.saved", saved.getPeriodLabel()));
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("flashMessage", errorFlash("tax.fil.onlyDraftEditable", null));
        }
        return "redirect:/taxes/filings";
    }

    private void validate(TaxFilingForm form, BindingResult bindingResult, Long excludeId) {
        if (form.periodFrom() != null && form.periodTo() != null
                && form.periodTo().isBefore(form.periodFrom())) {
            bindingResult.rejectValue("periodTo", "tax.fil.periodInvalid");
        }
        TaxFilingType type;
        try {
            type = TaxFilingType.valueOf(form.filingType().trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            return;
        }
        if (form.periodFrom() != null && form.periodTo() != null
                && filingService.periodAlreadyFiled(type, form.periodFrom(), form.periodTo(), excludeId)) {
            bindingResult.rejectValue("periodTo", "tax.fil.duplicate");
        }
    }

    // ----- Lifecycle -----

    @PostMapping("/{id}/submit")
    public String submit(@PathVariable Long id,
                         @RequestParam(value = "reference", required = false) String reference,
                         RedirectAttributes redirectAttributes) {
        try {
            TaxFiling filing = filingService.submit(id, reference);
            redirectAttributes.addFlashAttribute("flashMessage",
                    flash("tax.fil.submitted", filing.getPeriodLabel()));
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("flashMessage",
                    errorFlash("tax.fil.actionFailed", ex.getMessage()));
        }
        return "redirect:/taxes/filings";
    }

    @PostMapping("/{id}/pay")
    public String pay(@PathVariable Long id,
                      @RequestParam("amount") BigDecimal amount,
                      @RequestParam(value = "paidOn", required = false)
                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paidOn,
                      RedirectAttributes redirectAttributes) {
        try {
            filingService.recordPayment(id, amount, paidOn);
            redirectAttributes.addFlashAttribute("flashMessage", flash("tax.fil.paymentRecorded", null));
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("flashMessage",
                    errorFlash("tax.fil.actionFailed", ex.getMessage()));
        }
        return "redirect:/taxes/filings";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            filingService.delete(id);
            redirectAttributes.addFlashAttribute("flashMessage", flash("tax.fil.deleted", null));
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("flashMessage",
                    errorFlash("tax.fil.actionFailed", ex.getMessage()));
        }
        return "redirect:/taxes/filings";
    }
}
