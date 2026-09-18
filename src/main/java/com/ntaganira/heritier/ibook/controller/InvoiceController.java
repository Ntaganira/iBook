/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : InvoiceController.java
 * - Date      : 2026. 09. 17.
 * - User      : Hntaganira
 * - Desc      : Sales invoices web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.InvoiceForm;
import com.ntaganira.heritier.ibook.dto.InvoicePaymentForm;
import com.ntaganira.heritier.ibook.entity.Account;
import com.ntaganira.heritier.ibook.entity.Customer;
import com.ntaganira.heritier.ibook.entity.Invoice;
import com.ntaganira.heritier.ibook.entity.InvoiceLine;
import com.ntaganira.heritier.ibook.enums.AccountType;
import com.ntaganira.heritier.ibook.enums.DocumentStatus;
import com.ntaganira.heritier.ibook.enums.PaymentMethod;
import com.ntaganira.heritier.ibook.repository.CustomerRepository;
import com.ntaganira.heritier.ibook.entity.TaxRate;
import com.ntaganira.heritier.ibook.service.AccountingService;
import com.ntaganira.heritier.ibook.service.InventoryService;
import com.ntaganira.heritier.ibook.service.TaxRateService;
import com.ntaganira.heritier.ibook.service.AuditService;
import com.ntaganira.heritier.ibook.service.InvoiceService;
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
import org.springframework.web.util.UriUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/invoices")
public class InvoiceController {

    private static final int PAGE_SIZE = 25;

    private final InvoiceService invoiceService;
    private final AccountingService accountingService;
    private final CustomerRepository customerRepository;
    private final TaxRateService taxRateService;
    private final InventoryService inventoryService;
    private final MessageSource messageSource;

    public InvoiceController(InvoiceService invoiceService,
                             AccountingService accountingService,
                             CustomerRepository customerRepository,
                             TaxRateService taxRateService,
                             InventoryService inventoryService,
                             MessageSource messageSource) {
        this.invoiceService = invoiceService;
        this.accountingService = accountingService;
        this.customerRepository = customerRepository;
        this.taxRateService = taxRateService;
        this.inventoryService = inventoryService;
        this.messageSource = messageSource;
    }

    private String msg(String key) {
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
    }

    private Map<String, String> flash(String key, String detail) {
        return Map.of("title", msg(key), "detail", detail == null ? "" : detail, "type", "success");
    }

    private Map<String, String> errorFlash(String detail) {
        return Map.of("title", msg("inv.actionFailed"), "detail", detail == null ? "" : detail, "type", "error");
    }

    // ----- List -----

    @GetMapping
    public String invoices(@RequestParam(value = "q", required = false) String q,
                           @RequestParam(value = "status", required = false) String status,
                           @RequestParam(value = "customer", required = false) Long customerId,
                           @RequestParam(value = "from", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(value = "to", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           @RequestParam(value = "sort", defaultValue = "issueDate") String sort,
                           @RequestParam(value = "dir", defaultValue = "desc") String dir,
                           @RequestParam(value = "page", defaultValue = "0") int page,
                           Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "issueDate",
                "issueDate", "invoiceNo", "customerName", "dueDate", "total", "status");
        Page<Invoice> result = invoiceService.listInvoices(q, status, customerId, from, to,
                PageRequest.of(Math.max(page, 0), PAGE_SIZE, sp.sort()));

        model.addAttribute("invoices", result);
        model.addAttribute("summary", invoiceService.summary());
        model.addAttribute("q", q);
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("customerId", customerId);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("baseCurrency", invoiceService.baseCurrency());
        model.addAttribute("statusLabels", statusLabels());

        StringBuilder fq = new StringBuilder();
        appendParam(fq, "q", q == null ? null : UriUtils.encodeQueryParam(q, StandardCharsets.UTF_8));
        appendParam(fq, "status", status);
        appendParam(fq, "customer", customerId == null ? null : customerId.toString());
        appendParam(fq, "from", from == null ? null : from.toString());
        appendParam(fq, "to", to == null ? null : to.toString());
        SortSpec.addListContext(model, "/invoices", fq.isEmpty() ? "" : "?" + fq, sp);
        return "invoices/list";
    }

    // ----- Create / edit -----

    @GetMapping("/new")
    public String newInvoice(@RequestParam(value = "customer", required = false) Long customerId, Model model) {
        InvoiceForm form = InvoiceForm.empty();
        form.setCurrencyCode(invoiceService.baseCurrency());
        if (customerId != null) {
            form.setCustomerId(customerId);
        }
        model.addAttribute("mode", "create");
        model.addAttribute("editingId", null);
        model.addAttribute("form", form);
        addFormContext(model);
        model.addAttribute("nextNumber", invoiceService.previewNextNumber());
        return "invoices/form";
    }

    @GetMapping("/{id}/edit")
    public String editInvoice(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Invoice invoice = invoiceService.getInvoice(id);
        if (invoice == null) {
            return "redirect:/invoices";
        }
        if (!invoice.isEditable()) {
            redirectAttributes.addFlashAttribute("flashMessage", errorFlash(msg("inv.onlyDraftEditable")));
            return "redirect:/invoices/" + id;
        }
        model.addAttribute("mode", "edit");
        model.addAttribute("editingId", id);
        model.addAttribute("form", toForm(invoice));
        addFormContext(model);
        model.addAttribute("nextNumber", invoice.getInvoiceNo());
        return "invoices/form";
    }

    @PostMapping
    public String createInvoice(@ModelAttribute("form") InvoiceForm form,
                                BindingResult bindingResult, Model model,
                                RedirectAttributes redirectAttributes) {
        validate(form, bindingResult);
        if (bindingResult.hasErrors()) {
            model.addAttribute("mode", "create");
            model.addAttribute("editingId", null);
            addFormContext(model);
            model.addAttribute("nextNumber", invoiceService.previewNextNumber());
            return "invoices/form";
        }
        try {
            Invoice saved = invoiceService.saveInvoice(form, null, AuditService.currentUsername());
            redirectAttributes.addFlashAttribute("flashMessage",
                    flash("inv.saved", saved.getInvoiceNo() + " — " + saved.getCustomerName()));
            return "redirect:/invoices/" + saved.getId();
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("flashMessage", errorFlash(ex.getMessage()));
            return "redirect:/invoices/new";
        }
    }

    @PostMapping("/{id}")
    public String updateInvoice(@PathVariable Long id, @ModelAttribute("form") InvoiceForm form,
                                BindingResult bindingResult, Model model,
                                RedirectAttributes redirectAttributes) {
        validate(form, bindingResult);
        if (bindingResult.hasErrors()) {
            model.addAttribute("mode", "edit");
            model.addAttribute("editingId", id);
            addFormContext(model);
            Invoice existing = invoiceService.getInvoice(id);
            model.addAttribute("nextNumber", existing == null ? "" : existing.getInvoiceNo());
            return "invoices/form";
        }
        try {
            Invoice saved = invoiceService.saveInvoice(form, id, AuditService.currentUsername());
            redirectAttributes.addFlashAttribute("flashMessage",
                    flash("inv.saved", saved.getInvoiceNo() + " — " + saved.getCustomerName()));
            return "redirect:/invoices/" + saved.getId();
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("flashMessage", errorFlash(ex.getMessage()));
            return "redirect:/invoices/" + id + "/edit";
        }
    }

    // ----- View -----

    @GetMapping("/{id}")
    public String viewInvoice(@PathVariable Long id, Model model) {
        Invoice invoice = invoiceService.getInvoice(id);
        if (invoice == null) {
            return "redirect:/invoices";
        }
        model.addAttribute("invoice", invoice);
        model.addAttribute("payments", invoiceService.paymentsFor(id));
        model.addAttribute("journal", invoiceService.journalFor(invoice));
        model.addAttribute("company", accountingService.getCompany());
        model.addAttribute("customer", customerRepository.findById(invoice.getCustomerId()).orElse(null));
        model.addAttribute("baseCurrency", invoiceService.baseCurrency());
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("depositAccounts", depositAccounts());
        model.addAttribute("paymentMethods", paymentMethodLabels());
        model.addAttribute("paymentForm", new InvoicePaymentForm(LocalDate.now(),
                invoice.getBalanceDue(), PaymentMethod.BANK_TRANSFER.name(), null, null, null));
        return "invoices/view";
    }

    // ----- Actions -----

    @PostMapping("/{id}/post")
    public String post(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Invoice posted = invoiceService.postInvoice(id);
            redirectAttributes.addFlashAttribute("flashMessage", flash("inv.posted", posted.getInvoiceNo()));
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("flashMessage", errorFlash(ex.getMessage()));
        }
        return "redirect:/invoices/" + id;
    }

    @PostMapping("/{id}/send")
    public String send(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Invoice sent = invoiceService.markSent(id);
            redirectAttributes.addFlashAttribute("flashMessage", flash("inv.sent", sent.getInvoiceNo()));
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("flashMessage", errorFlash(ex.getMessage()));
        }
        return "redirect:/invoices/" + id;
    }

    @PostMapping("/{id}/payments")
    public String recordPayment(@PathVariable Long id, @ModelAttribute InvoicePaymentForm form,
                                RedirectAttributes redirectAttributes) {
        try {
            invoiceService.recordPayment(id, form);
            redirectAttributes.addFlashAttribute("flashMessage",
                    flash("inv.paymentRecorded", form.amount() == null ? "" : form.amount().toPlainString()));
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("flashMessage", errorFlash(ex.getMessage()));
        }
        return "redirect:/invoices/" + id;
    }

    @PostMapping("/{id}/void")
    public String voidInvoice(@PathVariable Long id,
                              @RequestParam(value = "reason", required = false) String reason,
                              RedirectAttributes redirectAttributes) {
        try {
            Invoice voided = invoiceService.voidInvoice(id, reason);
            redirectAttributes.addFlashAttribute("flashMessage", flash("inv.voided", voided.getInvoiceNo()));
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("flashMessage", errorFlash(ex.getMessage()));
        }
        return "redirect:/invoices/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            invoiceService.deleteDraft(id);
            redirectAttributes.addFlashAttribute("flashMessage", flash("inv.deleted", null));
            return "redirect:/invoices";
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("flashMessage", errorFlash(ex.getMessage()));
            return "redirect:/invoices/" + id;
        }
    }

    // ----- Helpers -----

    private void validate(InvoiceForm form, BindingResult bindingResult) {
        if (form.getCustomerId() == null) {
            bindingResult.rejectValue("customerId", "inv.customerRequired");
        }
        if (form.getIssueDate() == null) {
            bindingResult.rejectValue("issueDate", "inv.dateRequired");
        }
        if (form.getDueDate() != null && form.getIssueDate() != null
                && form.getDueDate().isBefore(form.getIssueDate())) {
            bindingResult.rejectValue("dueDate", "inv.dueBeforeIssue");
        }
        if (!form.hasContent()) {
            bindingResult.rejectValue("lines", "inv.noLines");
        }
        if (form.total().signum() < 0) {
            bindingResult.rejectValue("discountAmount", "inv.negativeTotal");
        }
    }

    private void addFormContext(Model model) {
        model.addAttribute("customers", customerRepository.findAll().stream()
                .filter(Customer::isActive)
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .toList());
        model.addAttribute("revenueAccounts", accountingService.listActiveAccounts().stream()
                .filter(a -> a.getType() == AccountType.REVENUE)
                .toList());
        model.addAttribute("baseCurrency", invoiceService.baseCurrency());
        model.addAttribute("taxRates", taxRateService.listActive());
        model.addAttribute("products", inventoryService.listSellableProducts());
        model.addAttribute("defaultVatRate", taxRateService.defaultRateValue());
        model.addAttribute("paymentTermsOptions", paymentTermsOptions());
    }

    private List<Account> depositAccounts() {
        return accountingService.listActiveAccounts().stream()
                .filter(a -> a.getType() == AccountType.ASSET)
                .filter(a -> a.getCode().startsWith("11") || a.getCode().startsWith("10"))
                .toList();
    }

    private Map<String, String> statusLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(DocumentStatus.DRAFT.name(), msg("common.status.draft"));
        labels.put(DocumentStatus.OPEN.name(), msg("common.status.open"));
        labels.put(DocumentStatus.PARTIALLY_PAID.name(), msg("common.status.partiallyPaid"));
        labels.put(DocumentStatus.PAID.name(), msg("common.status.paid"));
        labels.put(DocumentStatus.OVERDUE.name(), msg("common.status.overdue"));
        labels.put(DocumentStatus.VOID.name(), msg("common.status.void"));
        return labels;
    }

    private Map<String, String> paymentMethodLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(PaymentMethod.BANK_TRANSFER.name(), msg("inv.pay.bankTransfer"));
        labels.put(PaymentMethod.MOBILE_MONEY.name(), msg("inv.pay.mtnMobileMoney"));
        labels.put(PaymentMethod.CASH.name(), msg("inv.pay.cash"));
        labels.put(PaymentMethod.CARD.name(), msg("inv.pay.card"));
        labels.put(PaymentMethod.CHECK.name(), msg("inv.pay.check"));
        return labels;
    }

    private Map<String, String> paymentTermsOptions() {
        Map<String, String> terms = new LinkedHashMap<>();
        terms.put("DUE_ON_RECEIPT", msg("inv.terms.dueOnReceipt"));
        terms.put("NET7", msg("inv.terms.net7"));
        terms.put("NET15", msg("inv.terms.net15"));
        terms.put("NET30", msg("inv.terms.net30"));
        terms.put("NET60", msg("inv.terms.net60"));
        return terms;
    }

    private InvoiceForm toForm(Invoice invoice) {
        InvoiceForm form = new InvoiceForm();
        form.setCustomerId(invoice.getCustomerId());
        form.setIssueDate(invoice.getIssueDate());
        form.setDueDate(invoice.getDueDate());
        form.setPaymentTerms(invoice.getPaymentTerms());
        form.setReference(invoice.getReference());
        form.setCurrencyCode(invoice.getCurrencyCode());
        form.setDiscountAmount(invoice.getDiscountAmount() == null ? BigDecimal.ZERO : invoice.getDiscountAmount());
        form.setCustomerMessage(invoice.getCustomerMessage());
        form.setNotes(invoice.getNotes());
        List<InvoiceForm.Line> lines = new ArrayList<>();
        for (InvoiceLine source : invoice.getLines()) {
            InvoiceForm.Line line = new InvoiceForm.Line();
            line.setDescription(source.getDescription());
            line.setQuantity(source.getQuantity());
            line.setUnitPrice(source.getUnitPrice());
            line.setTaxRate(source.getTaxRate());
            line.setTaxRateId(source.getTaxRateId());
            line.setProductId(source.getProductId());
            line.setRevenueAccountId(source.getRevenueAccountId());
            lines.add(line);
        }
        if (lines.isEmpty()) {
            InvoiceForm.Line blank = new InvoiceForm.Line();
            blank.setQuantity(BigDecimal.ONE);
            blank.setTaxRate(taxRateService.defaultRateValue());
            TaxRate fallback = taxRateService.defaultRate();
            blank.setTaxRateId(fallback == null ? null : fallback.getId());
            lines.add(blank);
        }
        form.setLines(lines);
        return form;
    }

    private static void appendParam(StringBuilder sb, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append('&');
        }
        sb.append(name).append('=').append(value);
    }
}
