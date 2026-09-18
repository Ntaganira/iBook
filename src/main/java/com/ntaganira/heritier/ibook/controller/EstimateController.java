/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : EstimateController.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Customer estimates web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.EstimateForm;
import com.ntaganira.heritier.ibook.dto.InvoiceForm;
import com.ntaganira.heritier.ibook.entity.Estimate;
import com.ntaganira.heritier.ibook.entity.EstimateLine;
import com.ntaganira.heritier.ibook.entity.Invoice;
import com.ntaganira.heritier.ibook.enums.AccountType;
import com.ntaganira.heritier.ibook.enums.EstimateStatus;
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
@RequestMapping("/sales/estimates")
public class EstimateController {

    private static final int PAGE_SIZE = 20;

    private final EstimateService estimateService;
    private final CustomerService customerService;
    private final AccountingService accountingService;
    private final InventoryService inventoryService;
    private final TaxRateService taxRateService;
    private final MessageSource messageSource;

    public EstimateController(EstimateService estimateService,
                              CustomerService customerService,
                              AccountingService accountingService,
                              InventoryService inventoryService,
                              TaxRateService taxRateService,
                              MessageSource messageSource) {
        this.estimateService = estimateService;
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
        for (EstimateStatus s : EstimateStatus.values()) {
            m.put(s.name(), msg("est.status." + s.name().toLowerCase(Locale.ROOT)));
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
        model.addAttribute("baseCurrency", estimateService.baseCurrency());
        model.addAttribute("defaultVatRate", taxRateService.defaultRateValue());
        model.addAttribute("nextNumber", estimateService.previewNextNumber());
    }

    // ----- List -----

    @GetMapping
    public String estimates(@RequestParam(value = "q", required = false) String q,
                            @RequestParam(value = "status", required = false) String status,
                            @RequestParam(value = "customer", required = false) Long customerId,
                            @RequestParam(value = "sort", defaultValue = "estimateDate") String sort,
                            @RequestParam(value = "dir", defaultValue = "desc") String dir,
                            @RequestParam(value = "page", defaultValue = "0") int page,
                            Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "estimateDate", "estimateDate", "estimateNo",
                "customerName", "expiryDate", "total", "status");
        model.addAttribute("estimates",
                estimateService.list(q, status, customerId, PageRequest.of(page, PAGE_SIZE, sp.sort())));
        model.addAttribute("summary", estimateService.summary());
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("baseCurrency", estimateService.baseCurrency());
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
        SortSpec.addListContext(model, "/sales/estimates", fq.isEmpty() ? "" : "?" + fq, sp);
        return "sales/estimates";
    }

    // ----- Create / edit -----

    @GetMapping("/new")
    public String newEstimate(@RequestParam(value = "customer", required = false) Long customerId,
                              Model model) {
        EstimateForm form = EstimateForm.empty(taxRateService.defaultRateValue());
        form.setCurrencyCode(estimateService.baseCurrency());
        if (customerId != null) {
            form.setCustomerId(customerId);
        }
        addFormContext(model, "create", null);
        model.addAttribute("form", form);
        return "sales/estimate-form";
    }

    @GetMapping("/{id}/edit")
    public String editEstimate(@PathVariable Long id, Model model, RedirectAttributes ra) {
        Estimate e = estimateService.get(id);
        if (e == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("est.notFound", null));
            return "redirect:/sales/estimates";
        }
        if (!e.isEditable()) {
            ra.addFlashAttribute("flashMessage", errorFlash("est.notEditable", e.getEstimateNo()));
            return "redirect:/sales/estimates/" + id;
        }
        EstimateForm form = new EstimateForm();
        form.setCustomerId(e.getCustomerId());
        form.setEstimateDate(e.getEstimateDate());
        form.setExpiryDate(e.getExpiryDate());
        form.setReference(e.getReference());
        form.setCurrencyCode(e.getCurrencyCode());
        form.setDiscountAmount(e.getDiscountAmount());
        form.setCustomerMessage(e.getCustomerMessage());
        form.setNotes(e.getNotes());
        for (int i = 0; i < e.getLines().size(); i++) {
            EstimateLine src = e.getLines().get(i);
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
        return "sales/estimate-form";
    }

    @PostMapping
    public String create(@ModelAttribute("form") EstimateForm form, BindingResult br,
                         Model model, RedirectAttributes ra) {
        validate(form, br);
        if (br.hasErrors()) {
            addFormContext(model, "create", null);
            return "sales/estimate-form";
        }
        Estimate saved = estimateService.save(form, null, AuditService.currentUsername());
        ra.addFlashAttribute("flashMessage", flash("est.saved", saved.getEstimateNo()));
        return "redirect:/sales/estimates/" + saved.getId();
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute("form") EstimateForm form,
                         BindingResult br, Model model, RedirectAttributes ra) {
        validate(form, br);
        if (br.hasErrors()) {
            addFormContext(model, "edit", id);
            return "sales/estimate-form";
        }
        try {
            Estimate saved = estimateService.save(form, id, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("est.saved", saved.getEstimateNo()));
            return "redirect:/sales/estimates/" + saved.getId();
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("est.notEditable", null));
            return "redirect:/sales/estimates/" + id;
        }
    }

    private void validate(EstimateForm form, BindingResult br) {
        if (form.getCustomerId() == null) {
            br.rejectValue("customerId", "est.customerRequired");
        }
        if (form.getEstimateDate() == null) {
            br.rejectValue("estimateDate", "est.dateRequired");
        }
        if (form.getEstimateDate() != null && form.getExpiryDate() != null
                && form.getExpiryDate().isBefore(form.getEstimateDate())) {
            br.rejectValue("expiryDate", "est.expiryBeforeDate");
        }
        if (!form.hasContent()) {
            br.rejectValue("lines", "est.noLines");
        }
    }

    // ----- View -----

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model, RedirectAttributes ra) {
        Estimate e = estimateService.get(id);
        if (e == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("est.notFound", null));
            return "redirect:/sales/estimates";
        }
        model.addAttribute("estimate", e);
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("baseCurrency", estimateService.baseCurrency());
        return "sales/estimate-view";
    }

    // ----- Lifecycle -----

    @PostMapping("/{id}/send")
    public String send(@PathVariable Long id, RedirectAttributes ra) {
        estimateService.markSent(id);
        ra.addFlashAttribute("flashMessage", flash("est.sent", null));
        return "redirect:/sales/estimates/" + id;
    }

    @PostMapping("/{id}/accept")
    public String accept(@PathVariable Long id, RedirectAttributes ra) {
        try {
            estimateService.decide(id, true);
            ra.addFlashAttribute("flashMessage", flash("est.accepted", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("est.actionFailed", ex.getMessage()));
        }
        return "redirect:/sales/estimates/" + id;
    }

    @PostMapping("/{id}/decline")
    public String decline(@PathVariable Long id, RedirectAttributes ra) {
        try {
            estimateService.decide(id, false);
            ra.addFlashAttribute("flashMessage", flash("est.declined", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("est.actionFailed", ex.getMessage()));
        }
        return "redirect:/sales/estimates/" + id;
    }

    @PostMapping("/{id}/convert")
    public String convert(@PathVariable Long id, RedirectAttributes ra) {
        try {
            Invoice invoice = estimateService.convertToInvoice(id, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("est.converted", invoice.getInvoiceNo()));
            return "redirect:/invoices/" + invoice.getId();
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("est.actionFailed", ex.getMessage()));
            return "redirect:/sales/estimates/" + id;
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            estimateService.delete(id);
            ra.addFlashAttribute("flashMessage", flash("est.deleted", null));
            return "redirect:/sales/estimates";
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("est.actionFailed", ex.getMessage()));
            return "redirect:/sales/estimates/" + id;
        }
    }
}
