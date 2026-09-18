/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : CreditNoteController.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Customer credit notes web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.CreditNoteForm;
import com.ntaganira.heritier.ibook.dto.InvoiceForm;
import com.ntaganira.heritier.ibook.entity.CreditNote;
import com.ntaganira.heritier.ibook.entity.CreditNoteLine;
import com.ntaganira.heritier.ibook.entity.Invoice;
import com.ntaganira.heritier.ibook.enums.AccountType;
import com.ntaganira.heritier.ibook.enums.CreditNoteStatus;
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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/sales/credit-notes")
public class CreditNoteController {

    private static final int PAGE_SIZE = 20;

    private final CreditNoteService creditNoteService;
    private final CustomerService customerService;
    private final AccountingService accountingService;
    private final InventoryService inventoryService;
    private final TaxRateService taxRateService;
    private final MessageSource messageSource;

    public CreditNoteController(CreditNoteService creditNoteService,
                                CustomerService customerService,
                                AccountingService accountingService,
                                InventoryService inventoryService,
                                TaxRateService taxRateService,
                                MessageSource messageSource) {
        this.creditNoteService = creditNoteService;
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
        for (CreditNoteStatus s : CreditNoteStatus.values()) {
            m.put(s.name(), msg("cn.status." + s.name().toLowerCase(Locale.ROOT)));
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
        model.addAttribute("openInvoices", creditNoteService.creditableInvoices());
        model.addAttribute("baseCurrency", creditNoteService.baseCurrency());
        model.addAttribute("defaultVatRate", taxRateService.defaultRateValue());
        model.addAttribute("nextNumber", creditNoteService.previewNextNumber());
    }

    // ----- List -----

    @GetMapping
    public String creditNotes(@RequestParam(value = "q", required = false) String q,
                              @RequestParam(value = "status", required = false) String status,
                              @RequestParam(value = "customer", required = false) Long customerId,
                              @RequestParam(value = "sort", defaultValue = "creditDate") String sort,
                              @RequestParam(value = "dir", defaultValue = "desc") String dir,
                              @RequestParam(value = "page", defaultValue = "0") int page,
                              Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "creditDate", "creditDate", "creditNoteNo",
                "customerName", "invoiceNo", "total", "status");
        model.addAttribute("creditNotes", creditNoteService.list(q, status, customerId, null, null,
                PageRequest.of(page, PAGE_SIZE, sp.sort())));
        model.addAttribute("summary", creditNoteService.summary());
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("baseCurrency", creditNoteService.baseCurrency());
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
        SortSpec.addListContext(model, "/sales/credit-notes", fq.isEmpty() ? "" : "?" + fq, sp);
        return "sales/credit-notes";
    }

    // ----- Create / edit -----

    @GetMapping("/new")
    public String newCreditNote(@RequestParam(value = "customer", required = false) Long customerId,
                                @RequestParam(value = "invoice", required = false) Long invoiceId,
                                Model model) {
        CreditNoteForm form = CreditNoteForm.empty(taxRateService.defaultRateValue());
        form.setCurrencyCode(creditNoteService.baseCurrency());
        if (customerId != null) {
            form.setCustomerId(customerId);
        }
        if (invoiceId != null) {
            form.setInvoiceId(invoiceId);
        }
        addFormContext(model, "create", null);
        model.addAttribute("form", form);
        return "sales/credit-note-form";
    }

    @GetMapping("/{id}/edit")
    public String editCreditNote(@PathVariable Long id, Model model, RedirectAttributes ra) {
        CreditNote note = creditNoteService.get(id);
        if (note == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("cn.notFound", null));
            return "redirect:/sales/credit-notes";
        }
        if (!note.isEditable()) {
            ra.addFlashAttribute("flashMessage", errorFlash("cn.notEditable", note.getCreditNoteNo()));
            return "redirect:/sales/credit-notes/" + id;
        }
        CreditNoteForm form = new CreditNoteForm();
        form.setCustomerId(note.getCustomerId());
        form.setInvoiceId(note.getInvoiceId());
        form.setCreditDate(note.getCreditDate());
        form.setReference(note.getReference());
        form.setReason(note.getReason());
        form.setCurrencyCode(note.getCurrencyCode());
        form.setDiscountAmount(note.getDiscountAmount());
        form.setCustomerMessage(note.getCustomerMessage());
        form.setNotes(note.getNotes());
        form.setRestockItems(note.isRestockItems());
        for (int i = 0; i < note.getLines().size(); i++) {
            CreditNoteLine src = note.getLines().get(i);
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
        return "sales/credit-note-form";
    }

    @PostMapping
    public String create(@ModelAttribute("form") CreditNoteForm form, BindingResult br,
                         Model model, RedirectAttributes ra) {
        validate(form, br);
        if (br.hasErrors()) {
            addFormContext(model, "create", null);
            return "sales/credit-note-form";
        }
        try {
            CreditNote saved = creditNoteService.save(form, null, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("cn.saved", saved.getCreditNoteNo()));
            return "redirect:/sales/credit-notes/" + saved.getId();
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("cn.actionFailed", ex.getMessage()));
            return "redirect:/sales/credit-notes/new";
        }
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute("form") CreditNoteForm form,
                         BindingResult br, Model model, RedirectAttributes ra) {
        validate(form, br);
        if (br.hasErrors()) {
            addFormContext(model, "edit", id);
            return "sales/credit-note-form";
        }
        try {
            CreditNote saved = creditNoteService.save(form, id, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("cn.saved", saved.getCreditNoteNo()));
            return "redirect:/sales/credit-notes/" + saved.getId();
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("cn.actionFailed", ex.getMessage()));
            return "redirect:/sales/credit-notes/" + id;
        }
    }

    private void validate(CreditNoteForm form, BindingResult br) {
        if (form.getCustomerId() == null) {
            br.rejectValue("customerId", "cn.customerRequired");
        }
        if (form.getCreditDate() == null) {
            br.rejectValue("creditDate", "cn.dateRequired");
        }
        if (!form.hasContent()) {
            br.rejectValue("lines", "cn.noLines");
        }
    }

    // ----- View -----

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model, RedirectAttributes ra) {
        CreditNote note = creditNoteService.get(id);
        if (note == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("cn.notFound", null));
            return "redirect:/sales/credit-notes";
        }
        Invoice invoice = creditNoteService.linkedInvoice(note);
        model.addAttribute("creditNote", note);
        model.addAttribute("invoice", invoice);
        model.addAttribute("journal", creditNoteService.journalFor(note));
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("baseCurrency", creditNoteService.baseCurrency());
        return "sales/credit-note-view";
    }

    // ----- Lifecycle -----

    @PostMapping("/{id}/post")
    public String post(@PathVariable Long id, RedirectAttributes ra) {
        try {
            CreditNote posted = creditNoteService.post(id);
            ra.addFlashAttribute("flashMessage", flash("cn.posted", posted.getCreditNoteNo()));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("cn.actionFailed", ex.getMessage()));
        }
        return "redirect:/sales/credit-notes/" + id;
    }

    @PostMapping("/{id}/void")
    public String voidCreditNote(@PathVariable Long id,
                                 @RequestParam(value = "reason", required = false) String reason,
                                 RedirectAttributes ra) {
        try {
            creditNoteService.voidCreditNote(id, reason);
            ra.addFlashAttribute("flashMessage", flash("cn.voided", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("cn.actionFailed", ex.getMessage()));
        }
        return "redirect:/sales/credit-notes/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            creditNoteService.delete(id);
            ra.addFlashAttribute("flashMessage", flash("cn.deleted", null));
            return "redirect:/sales/credit-notes";
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("cn.actionFailed", ex.getMessage()));
            return "redirect:/sales/credit-notes/" + id;
        }
    }
}
