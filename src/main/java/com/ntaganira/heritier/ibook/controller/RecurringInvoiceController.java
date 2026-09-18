/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : RecurringInvoiceController.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Recurring invoice schedules web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.InvoiceForm;
import com.ntaganira.heritier.ibook.dto.RecurringInvoiceForm;
import com.ntaganira.heritier.ibook.entity.Invoice;
import com.ntaganira.heritier.ibook.entity.RecurringInvoice;
import com.ntaganira.heritier.ibook.entity.RecurringInvoiceLine;
import com.ntaganira.heritier.ibook.enums.AccountType;
import com.ntaganira.heritier.ibook.enums.RecurrenceFrequency;
import com.ntaganira.heritier.ibook.enums.RecurringInvoiceStatus;
import com.ntaganira.heritier.ibook.service.*;
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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/sales/recurring")
public class RecurringInvoiceController {

    private static final int PAGE_SIZE = 20;

    private final RecurringInvoiceService recurringInvoiceService;
    private final CustomerService customerService;
    private final AccountingService accountingService;
    private final InventoryService inventoryService;
    private final TaxRateService taxRateService;
    private final MessageSource messageSource;

    public RecurringInvoiceController(RecurringInvoiceService recurringInvoiceService,
                                      CustomerService customerService,
                                      AccountingService accountingService,
                                      InventoryService inventoryService,
                                      TaxRateService taxRateService,
                                      MessageSource messageSource) {
        this.recurringInvoiceService = recurringInvoiceService;
        this.customerService = customerService;
        this.accountingService = accountingService;
        this.inventoryService = inventoryService;
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

    private Map<String, String> statusLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (RecurringInvoiceStatus s : RecurringInvoiceStatus.values()) {
            m.put(s.name(), msg("rec.status." + s.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private Map<String, String> frequencyLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (RecurrenceFrequency f : RecurrenceFrequency.values()) {
            m.put(f.name(), msg("rec.freq." + f.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private void addFormContext(Model model, String mode, Long editingId) {
        model.addAttribute("mode", mode);
        model.addAttribute("editingId", editingId);
        model.addAttribute("customers", customerService.listActiveCustomers());
        model.addAttribute("revenueAccounts", accountingService.listActiveAccounts().stream()
                .filter(a -> a.getType() == AccountType.REVENUE).toList());
        model.addAttribute("taxRates", taxRateService.listActive());
        model.addAttribute("products", inventoryService.listSellableProducts());
        model.addAttribute("frequencyLabels", frequencyLabels());
        model.addAttribute("baseCurrency", recurringInvoiceService.baseCurrency());
        model.addAttribute("defaultVatRate", taxRateService.defaultRateValue());
    }

    // ----- List -----

    @GetMapping
    public String schedules(@RequestParam(value = "q", required = false) String q,
                            @RequestParam(value = "status", required = false) String status,
                            @RequestParam(value = "customer", required = false) Long customerId,
                            @RequestParam(value = "sort", defaultValue = "nextRunDate") String sort,
                            @RequestParam(value = "dir", defaultValue = "asc") String dir,
                            @RequestParam(value = "page", defaultValue = "0") int page,
                            Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "nextRunDate", "nextRunDate", "name",
                "customerName", "frequency", "total", "status");
        model.addAttribute("schedules", recurringInvoiceService.list(q, status, customerId,
                PageRequest.of(page, PAGE_SIZE, sp.sort())));
        model.addAttribute("summary", recurringInvoiceService.summary());
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("frequencyLabels", frequencyLabels());
        model.addAttribute("baseCurrency", recurringInvoiceService.baseCurrency());
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
        SortSpec.addListContext(model, "/sales/recurring", fq.isEmpty() ? "" : "?" + fq, sp);
        return "sales/recurring";
    }

    // ----- Create / edit -----

    @GetMapping("/new")
    public String newSchedule(@RequestParam(value = "customer", required = false) Long customerId,
                              Model model) {
        RecurringInvoiceForm form = RecurringInvoiceForm.empty(taxRateService.defaultRateValue());
        form.setCurrencyCode(recurringInvoiceService.baseCurrency());
        if (customerId != null) {
            form.setCustomerId(customerId);
        }
        addFormContext(model, "create", null);
        model.addAttribute("form", form);
        return "sales/recurring-form";
    }

    @GetMapping("/{id}/edit")
    public String editSchedule(@PathVariable Long id, Model model, RedirectAttributes ra) {
        RecurringInvoice schedule = recurringInvoiceService.get(id);
        if (schedule == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("rec.notFound", null));
            return "redirect:/sales/recurring";
        }
        if (!schedule.isEditable()) {
            ra.addFlashAttribute("flashMessage", errorFlash("rec.notEditable", schedule.getName()));
            return "redirect:/sales/recurring/" + id;
        }
        RecurringInvoiceForm form = new RecurringInvoiceForm();
        form.setName(schedule.getName());
        form.setCustomerId(schedule.getCustomerId());
        form.setFrequency(schedule.getFrequency() == null ? null : schedule.getFrequency().name());
        form.setStartDate(schedule.getStartDate());
        form.setEndDate(schedule.getEndDate());
        form.setMaxOccurrences(schedule.getMaxOccurrences());
        form.setDueDays(schedule.getDueDays());
        form.setAutoPost(schedule.isAutoPost());
        form.setReference(schedule.getReference());
        form.setCurrencyCode(schedule.getCurrencyCode());
        form.setDiscountAmount(schedule.getDiscountAmount());
        form.setCustomerMessage(schedule.getCustomerMessage());
        form.setNotes(schedule.getNotes());
        for (int i = 0; i < schedule.getLines().size(); i++) {
            RecurringInvoiceLine src = schedule.getLines().get(i);
            InvoiceForm.Line line = form.getLines().get(i);
            line.setDescription(src.getDescription());
            line.setProductId(src.getProductId());
            line.setQuantity(src.getQuantity());
            line.setUnitPrice(src.getUnitPrice());
            line.setTaxRate(src.getTaxRate());
            line.setTaxRateId(src.getTaxRateId());
            line.setRevenueAccountId(src.getRevenueAccountId());
        }
        addFormContext(model, "edit", id);
        model.addAttribute("form", form);
        return "sales/recurring-form";
    }

    @PostMapping
    public String create(@ModelAttribute("form") RecurringInvoiceForm form, BindingResult br,
                         Model model, RedirectAttributes ra) {
        validate(form, br, null);
        if (br.hasErrors()) {
            addFormContext(model, "create", null);
            return "sales/recurring-form";
        }
        RecurringInvoice saved = recurringInvoiceService.save(form, null, AuditService.currentUsername());
        ra.addFlashAttribute("flashMessage", flash("rec.saved", saved.getName()));
        return "redirect:/sales/recurring/" + saved.getId();
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute("form") RecurringInvoiceForm form,
                         BindingResult br, Model model, RedirectAttributes ra) {
        validate(form, br, id);
        if (br.hasErrors()) {
            addFormContext(model, "edit", id);
            return "sales/recurring-form";
        }
        try {
            RecurringInvoice saved = recurringInvoiceService.save(form, id, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("rec.saved", saved.getName()));
            return "redirect:/sales/recurring/" + saved.getId();
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("rec.actionFailed", ex.getMessage()));
            return "redirect:/sales/recurring/" + id;
        }
    }

    private void validate(RecurringInvoiceForm form, BindingResult br, Long excludeId) {
        if (form.getName() == null || form.getName().isBlank()) {
            br.rejectValue("name", "rec.nameRequired");
        } else if (recurringInvoiceService.nameExists(form.getName(), excludeId)) {
            br.rejectValue("name", "rec.nameExists");
        }
        if (form.getCustomerId() == null) {
            br.rejectValue("customerId", "rec.customerRequired");
        }
        if (form.getStartDate() == null) {
            br.rejectValue("startDate", "rec.startRequired");
        }
        if (form.getStartDate() != null && form.getEndDate() != null
                && form.getEndDate().isBefore(form.getStartDate())) {
            br.rejectValue("endDate", "rec.endBeforeStart");
        }
        if (form.getDueDays() != null && form.getDueDays() < 0) {
            br.rejectValue("dueDays", "rec.dueDaysNegative");
        }
        if (!form.hasContent()) {
            br.rejectValue("lines", "rec.noLines");
        }
    }

    // ----- View -----

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model, RedirectAttributes ra) {
        RecurringInvoice schedule = recurringInvoiceService.get(id);
        if (schedule == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("rec.notFound", null));
            return "redirect:/sales/recurring";
        }
        model.addAttribute("schedule", schedule);
        model.addAttribute("history", recurringInvoiceService.history(id));
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("frequencyLabels", frequencyLabels());
        model.addAttribute("baseCurrency", recurringInvoiceService.baseCurrency());
        return "sales/recurring-view";
    }

    // ----- Lifecycle -----

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id, RedirectAttributes ra) {
        try {
            recurringInvoiceService.activate(id);
            ra.addFlashAttribute("flashMessage", flash("rec.activated", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("rec.actionFailed", ex.getMessage()));
        }
        return "redirect:/sales/recurring/" + id;
    }

    @PostMapping("/{id}/pause")
    public String pause(@PathVariable Long id, RedirectAttributes ra) {
        try {
            recurringInvoiceService.pause(id);
            ra.addFlashAttribute("flashMessage", flash("rec.paused", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("rec.actionFailed", ex.getMessage()));
        }
        return "redirect:/sales/recurring/" + id;
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id,
                         @RequestParam(value = "reason", required = false) String reason,
                         RedirectAttributes ra) {
        try {
            recurringInvoiceService.cancel(id, reason);
            ra.addFlashAttribute("flashMessage", flash("rec.cancelled", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("rec.actionFailed", ex.getMessage()));
        }
        return "redirect:/sales/recurring/" + id;
    }

    @PostMapping("/{id}/generate")
    public String generate(@PathVariable Long id, RedirectAttributes ra) {
        try {
            Invoice invoice = recurringInvoiceService.generateNow(id, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("rec.generated", invoice.getInvoiceNo()));
            return "redirect:/invoices/" + invoice.getId();
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("rec.actionFailed", ex.getMessage()));
            return "redirect:/sales/recurring/" + id;
        }
    }

    @PostMapping("/run")
    public String runDue(RedirectAttributes ra) {
        RecurringInvoiceService.RunReport report =
                recurringInvoiceService.runDue(LocalDate.now(), AuditService.currentUsername());
        if (report.isEmpty()) {
            ra.addFlashAttribute("flashMessage", flash("rec.runNothing", null));
        } else {
            ra.addFlashAttribute("flashMessage", flash("rec.runDone",
                    messageSource.getMessage("rec.runDetail",
                            new Object[]{report.invoices(), report.schedules()},
                            LocaleContextHolder.getLocale())));
        }
        return "redirect:/sales/recurring";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            recurringInvoiceService.delete(id);
            ra.addFlashAttribute("flashMessage", flash("rec.deleted", null));
            return "redirect:/sales/recurring";
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("rec.actionFailed", ex.getMessage()));
            return "redirect:/sales/recurring/" + id;
        }
    }
}
