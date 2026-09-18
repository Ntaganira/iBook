/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : BillController.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Vendor bills (accounts payable) web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.BillForm;
import com.ntaganira.heritier.ibook.dto.BillPaymentForm;
import com.ntaganira.heritier.ibook.entity.Account;
import com.ntaganira.heritier.ibook.entity.Bill;
import com.ntaganira.heritier.ibook.entity.Vendor;
import com.ntaganira.heritier.ibook.enums.AccountType;
import com.ntaganira.heritier.ibook.enums.DocumentStatus;
import com.ntaganira.heritier.ibook.enums.PaymentMethod;
import com.ntaganira.heritier.ibook.service.AccountingService;
import com.ntaganira.heritier.ibook.service.InventoryService;
import com.ntaganira.heritier.ibook.service.TaxRateService;
import com.ntaganira.heritier.ibook.service.AuditService;
import com.ntaganira.heritier.ibook.service.BillService;
import com.ntaganira.heritier.ibook.service.VendorService;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/bills")
public class BillController {

    private static final int PAGE_SIZE = 20;

    private final BillService billService;
    private final VendorService vendorService;
    private final AccountingService accountingService;
    private final TaxRateService taxRateService;
    private final InventoryService inventoryService;
    private final MessageSource messageSource;

    public BillController(BillService billService,
                          VendorService vendorService,
                          AccountingService accountingService,
                          TaxRateService taxRateService,
                          InventoryService inventoryService,
                          MessageSource messageSource) {
        this.billService = billService;
        this.vendorService = vendorService;
        this.accountingService = accountingService;
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

    private Map<String, String> errorFlash(String key, String detail) {
        return Map.of("title", msg(key), "detail", detail == null ? "" : detail, "type", "error");
    }

    private Map<String, String> statusLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        for (DocumentStatus status : DocumentStatus.values()) {
            labels.put(status.name(), msg(statusKey(status)));
        }
        return labels;
    }

    private static String statusKey(DocumentStatus status) {
        return switch (status) {
            case DRAFT -> "common.status.draft";
            case OPEN -> "common.status.open";
            case PARTIALLY_PAID -> "common.status.partiallyPaid";
            case PAID -> "common.status.paid";
            case OVERDUE -> "common.status.overdue";
            case VOID -> "common.status.void";
        };
    }

    private Map<String, String> paymentMethods() {
        Map<String, String> methods = new LinkedHashMap<>();
        methods.put(PaymentMethod.BANK_TRANSFER.name(), msg("inv.pay.bankTransfer"));
        methods.put(PaymentMethod.CASH.name(), msg("inv.pay.cash"));
        methods.put(PaymentMethod.MOBILE_MONEY.name(), msg("inv.pay.mtnMobileMoney"));
        methods.put(PaymentMethod.CARD.name(), msg("inv.pay.card"));
        methods.put(PaymentMethod.CHECK.name(), msg("inv.pay.check"));
        return methods;
    }

    private Map<String, String> paymentTermsOptions() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("Due on receipt", msg("inv.terms.dueOnReceipt"));
        options.put("Net 7", msg("inv.terms.net7"));
        options.put("Net 15", msg("inv.terms.net15"));
        options.put("Net 30", msg("inv.terms.net30"));
        options.put("Net 60", msg("inv.terms.net60"));
        return options;
    }

    private List<Account> expenseAccounts() {
        return accountingService.listActiveAccounts().stream()
                .filter(a -> a.getType() == AccountType.EXPENSE || a.getType() == AccountType.ASSET)
                .toList();
    }

    private List<Account> sourceAccounts() {
        return accountingService.listActiveAccounts().stream()
                .filter(a -> a.getType() == AccountType.ASSET)
                .filter(a -> a.getCode().startsWith("11") || a.getCode().startsWith("10"))
                .toList();
    }

    private void addFormContext(Model model, String mode, Long editingId) {
        model.addAttribute("mode", mode);
        model.addAttribute("editingId", editingId);
        model.addAttribute("vendors", vendorService.listActiveVendors());
        model.addAttribute("expenseAccounts", expenseAccounts());
        model.addAttribute("paymentTermsOptions", paymentTermsOptions());
        model.addAttribute("baseCurrency", billService.baseCurrency());
        model.addAttribute("taxRates", taxRateService.listActive());
        model.addAttribute("products", inventoryService.listSellableProducts());
        model.addAttribute("defaultVatRate", taxRateService.defaultRateValue());
        model.addAttribute("nextNumber", billService.previewNextNumber());
    }

    // ----- List -----

    @GetMapping
    public String bills(@RequestParam(value = "q", required = false) String q,
                        @RequestParam(value = "status", required = false) String status,
                        @RequestParam(value = "vendor", required = false) Long vendorId,
                        @RequestParam(value = "from", required = false)
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                        @RequestParam(value = "to", required = false)
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                        @RequestParam(value = "sort", defaultValue = "billDate") String sort,
                        @RequestParam(value = "dir", defaultValue = "desc") String dir,
                        @RequestParam(value = "page", defaultValue = "0") int page,
                        Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "billDate", "billDate", "billNo", "vendorName", "dueDate",
                "total", "status");
        Page<Bill> result = billService.listBills(q, status, vendorId, from, to,
                PageRequest.of(page, PAGE_SIZE, sp.sort()));
        model.addAttribute("bills", result);
        model.addAttribute("summary", billService.summary());
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("baseCurrency", billService.baseCurrency());
        model.addAttribute("q", q);
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
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
        SortSpec.addListContext(model, "/bills", fq.isEmpty() ? "" : "?" + fq, sp);
        return "bills/list";
    }

    // ----- Create / edit -----

    @GetMapping("/new")
    public String newBill(@RequestParam(value = "vendor", required = false) Long vendorId, Model model) {
        BillForm form = BillForm.empty();
        form.setCurrencyCode(billService.baseCurrency());
        if (vendorId != null) {
            Vendor vendor = vendorService.getVendor(vendorId);
            if (vendor != null) {
                form.setVendorId(vendor.getId());
                if (vendor.getPaymentTerms() != null) {
                    form.setPaymentTerms(vendor.getPaymentTerms());
                }
            }
        }
        addFormContext(model, "create", null);
        model.addAttribute("form", form);
        return "bills/form";
    }

    @GetMapping("/{id}/edit")
    public String editBill(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Bill bill = billService.getBill(id);
        if (bill == null) {
            redirectAttributes.addFlashAttribute("flashMessage", errorFlash("bill.notFound", null));
            return "redirect:/bills";
        }
        if (!bill.isEditable()) {
            redirectAttributes.addFlashAttribute("flashMessage", errorFlash("bill.onlyDraftEditable", bill.getBillNo()));
            return "redirect:/bills/" + id;
        }
        BillForm form = new BillForm();
        form.setVendorId(bill.getVendorId());
        form.setVendorInvoiceNo(bill.getVendorInvoiceNo());
        form.setBillDate(bill.getBillDate());
        form.setDueDate(bill.getDueDate());
        form.setPaymentTerms(bill.getPaymentTerms());
        form.setReference(bill.getReference());
        form.setCurrencyCode(bill.getCurrencyCode());
        form.setDiscountAmount(bill.getDiscountAmount());
        form.setMemo(bill.getMemo());
        form.setNotes(bill.getNotes());
        for (int i = 0; i < bill.getLines().size(); i++) {
            BillForm.Line line = form.getLines().get(i);
            line.setDescription(bill.getLines().get(i).getDescription());
            line.setQuantity(bill.getLines().get(i).getQuantity());
            line.setUnitPrice(bill.getLines().get(i).getUnitPrice());
            line.setTaxRate(bill.getLines().get(i).getTaxRate());
            line.setTaxRateId(bill.getLines().get(i).getTaxRateId());
            line.setProductId(bill.getLines().get(i).getProductId());
            line.setExpenseAccountId(bill.getLines().get(i).getExpenseAccountId());
        }
        addFormContext(model, "edit", id);
        model.addAttribute("form", form);
        return "bills/form";
    }

    @PostMapping
    public String createBill(@ModelAttribute("form") BillForm form, BindingResult bindingResult,
                             Model model, RedirectAttributes redirectAttributes) {
        validate(form, bindingResult);
        if (bindingResult.hasErrors()) {
            addFormContext(model, "create", null);
            return "bills/form";
        }
        Bill saved = billService.saveBill(form, null, AuditService.currentUsername());
        redirectAttributes.addFlashAttribute("flashMessage",
                flash(form.isPostNow() ? "bill.posted" : "bill.saved", saved.getBillNo()));
        return "redirect:/bills/" + saved.getId();
    }

    @PostMapping("/{id}")
    public String updateBill(@PathVariable Long id, @ModelAttribute("form") BillForm form,
                             BindingResult bindingResult, Model model,
                             RedirectAttributes redirectAttributes) {
        validate(form, bindingResult);
        if (bindingResult.hasErrors()) {
            addFormContext(model, "edit", id);
            return "bills/form";
        }
        try {
            Bill saved = billService.saveBill(form, id, AuditService.currentUsername());
            redirectAttributes.addFlashAttribute("flashMessage",
                    flash(form.isPostNow() ? "bill.posted" : "bill.saved", saved.getBillNo()));
            return "redirect:/bills/" + saved.getId();
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("flashMessage", errorFlash("bill.onlyDraftEditable", null));
            return "redirect:/bills/" + id;
        }
    }

    private void validate(BillForm form, BindingResult bindingResult) {
        if (form.getVendorId() == null) {
            bindingResult.rejectValue("vendorId", "bill.vendorRequired");
        }
        if (form.getBillDate() == null) {
            bindingResult.rejectValue("billDate", "bill.dateRequired");
        }
        if (form.getBillDate() != null && form.getDueDate() != null
                && form.getDueDate().isBefore(form.getBillDate())) {
            bindingResult.rejectValue("dueDate", "bill.dueBeforeBill");
        }
        if (!form.hasContent()) {
            bindingResult.rejectValue("lines", "bill.noLines");
        }
        if (form.hasContent() && form.total().signum() < 0) {
            bindingResult.rejectValue("discountAmount", "bill.negativeTotal");
        }
    }

    // ----- View -----

    @GetMapping("/{id}")
    public String viewBill(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Bill bill = billService.getBill(id);
        if (bill == null) {
            redirectAttributes.addFlashAttribute("flashMessage", errorFlash("bill.notFound", null));
            return "redirect:/bills";
        }
        model.addAttribute("bill", bill);
        model.addAttribute("vendor", vendorService.getVendor(bill.getVendorId()));
        model.addAttribute("payments", billService.paymentsFor(id));
        model.addAttribute("journal", billService.journalFor(bill));
        model.addAttribute("sourceAccounts", sourceAccounts());
        model.addAttribute("paymentMethods", paymentMethods());
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("baseCurrency", billService.baseCurrency());
        return "bills/view";
    }

    // ----- Lifecycle -----

    @PostMapping("/{id}/post")
    public String post(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Bill bill = billService.postBill(id);
            redirectAttributes.addFlashAttribute("flashMessage", flash("bill.posted", bill.getBillNo()));
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("flashMessage", errorFlash("bill.actionFailed", ex.getMessage()));
        }
        return "redirect:/bills/" + id;
    }

    @PostMapping("/{id}/payments")
    public String recordPayment(@PathVariable Long id, @ModelAttribute BillPaymentForm form,
                                RedirectAttributes redirectAttributes) {
        try {
            billService.recordPayment(id, form);
            redirectAttributes.addFlashAttribute("flashMessage", flash("bill.paymentRecorded", null));
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("flashMessage", errorFlash("bill.actionFailed", ex.getMessage()));
        }
        return "redirect:/bills/" + id;
    }

    @PostMapping("/{id}/void")
    public String voidBill(@PathVariable Long id,
                           @RequestParam(value = "reason", required = false) String reason,
                           RedirectAttributes redirectAttributes) {
        try {
            Bill bill = billService.voidBill(id, reason);
            redirectAttributes.addFlashAttribute("flashMessage", flash("bill.voided", bill.getBillNo()));
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("flashMessage", errorFlash("bill.actionFailed", ex.getMessage()));
        }
        return "redirect:/bills/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            billService.deleteDraft(id);
            redirectAttributes.addFlashAttribute("flashMessage", flash("bill.deleted", null));
            return "redirect:/bills";
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("flashMessage", errorFlash("bill.actionFailed", ex.getMessage()));
            return "redirect:/bills/" + id;
        }
    }
}
