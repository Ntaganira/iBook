/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : SalesOrderController.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Customer sales orders web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.InvoiceForm;
import com.ntaganira.heritier.ibook.dto.SalesOrderForm;
import com.ntaganira.heritier.ibook.entity.Invoice;
import com.ntaganira.heritier.ibook.entity.SalesOrder;
import com.ntaganira.heritier.ibook.entity.SalesOrderLine;
import com.ntaganira.heritier.ibook.enums.AccountType;
import com.ntaganira.heritier.ibook.enums.SalesOrderStatus;
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
@RequestMapping("/sales/orders")
public class SalesOrderController {

    private static final int PAGE_SIZE = 20;

    private final SalesOrderService salesOrderService;
    private final CustomerService customerService;
    private final AccountingService accountingService;
    private final InventoryService inventoryService;
    private final TaxRateService taxRateService;
    private final MessageSource messageSource;

    public SalesOrderController(SalesOrderService salesOrderService,
                                CustomerService customerService,
                                AccountingService accountingService,
                                InventoryService inventoryService,
                                TaxRateService taxRateService,
                                MessageSource messageSource) {
        this.salesOrderService = salesOrderService;
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
        for (SalesOrderStatus s : SalesOrderStatus.values()) {
            m.put(s.name(), msg("so.status." + s.name().toLowerCase(Locale.ROOT)));
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
        model.addAttribute("baseCurrency", salesOrderService.baseCurrency());
        model.addAttribute("defaultVatRate", taxRateService.defaultRateValue());
        model.addAttribute("nextNumber", salesOrderService.previewNextNumber());
    }

    // ----- List -----

    @GetMapping
    public String orders(@RequestParam(value = "q", required = false) String q,
                         @RequestParam(value = "status", required = false) String status,
                         @RequestParam(value = "customer", required = false) Long customerId,
                         @RequestParam(value = "sort", defaultValue = "orderDate") String sort,
                         @RequestParam(value = "dir", defaultValue = "desc") String dir,
                         @RequestParam(value = "page", defaultValue = "0") int page,
                         Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "orderDate", "orderDate", "orderNo",
                "customerName", "expectedDate", "total", "status");
        model.addAttribute("orders",
                salesOrderService.list(q, status, customerId, PageRequest.of(page, PAGE_SIZE, sp.sort())));
        model.addAttribute("summary", salesOrderService.summary());
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("baseCurrency", salesOrderService.baseCurrency());
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
        SortSpec.addListContext(model, "/sales/orders", fq.isEmpty() ? "" : "?" + fq, sp);
        return "sales/orders";
    }

    // ----- Create / edit -----

    @GetMapping("/new")
    public String newOrder(@RequestParam(value = "customer", required = false) Long customerId,
                           Model model) {
        SalesOrderForm form = SalesOrderForm.empty(taxRateService.defaultRateValue());
        form.setCurrencyCode(salesOrderService.baseCurrency());
        if (customerId != null) {
            form.setCustomerId(customerId);
        }
        addFormContext(model, "create", null);
        model.addAttribute("form", form);
        return "sales/order-form";
    }

    @GetMapping("/{id}/edit")
    public String editOrder(@PathVariable Long id, Model model, RedirectAttributes ra) {
        SalesOrder order = salesOrderService.get(id);
        if (order == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("so.notFound", null));
            return "redirect:/sales/orders";
        }
        if (!order.isEditable()) {
            ra.addFlashAttribute("flashMessage", errorFlash("so.notEditable", order.getOrderNo()));
            return "redirect:/sales/orders/" + id;
        }
        SalesOrderForm form = new SalesOrderForm();
        form.setCustomerId(order.getCustomerId());
        form.setOrderDate(order.getOrderDate());
        form.setExpectedDate(order.getExpectedDate());
        form.setReference(order.getReference());
        form.setCurrencyCode(order.getCurrencyCode());
        form.setDiscountAmount(order.getDiscountAmount());
        form.setCustomerMessage(order.getCustomerMessage());
        form.setNotes(order.getNotes());
        for (int i = 0; i < order.getLines().size(); i++) {
            SalesOrderLine src = order.getLines().get(i);
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
        return "sales/order-form";
    }

    @PostMapping
    public String create(@ModelAttribute("form") SalesOrderForm form, BindingResult br,
                         Model model, RedirectAttributes ra) {
        validate(form, br);
        if (br.hasErrors()) {
            addFormContext(model, "create", null);
            return "sales/order-form";
        }
        SalesOrder saved = salesOrderService.save(form, null, AuditService.currentUsername());
        ra.addFlashAttribute("flashMessage", flash("so.saved", saved.getOrderNo()));
        return "redirect:/sales/orders/" + saved.getId();
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute("form") SalesOrderForm form,
                         BindingResult br, Model model, RedirectAttributes ra) {
        validate(form, br);
        if (br.hasErrors()) {
            addFormContext(model, "edit", id);
            return "sales/order-form";
        }
        try {
            SalesOrder saved = salesOrderService.save(form, id, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("so.saved", saved.getOrderNo()));
            return "redirect:/sales/orders/" + saved.getId();
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("so.notEditable", null));
            return "redirect:/sales/orders/" + id;
        }
    }

    private void validate(SalesOrderForm form, BindingResult br) {
        if (form.getCustomerId() == null) {
            br.rejectValue("customerId", "so.customerRequired");
        }
        if (form.getOrderDate() == null) {
            br.rejectValue("orderDate", "so.dateRequired");
        }
        if (form.getOrderDate() != null && form.getExpectedDate() != null
                && form.getExpectedDate().isBefore(form.getOrderDate())) {
            br.rejectValue("expectedDate", "so.expectedBeforeDate");
        }
        if (!form.hasContent()) {
            br.rejectValue("lines", "so.noLines");
        }
    }

    // ----- View -----

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model, RedirectAttributes ra) {
        SalesOrder order = salesOrderService.get(id);
        if (order == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("so.notFound", null));
            return "redirect:/sales/orders";
        }
        model.addAttribute("order", order);
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("baseCurrency", salesOrderService.baseCurrency());
        return "sales/order-view";
    }

    // ----- Lifecycle -----

    @PostMapping("/{id}/confirm")
    public String confirm(@PathVariable Long id, RedirectAttributes ra) {
        try {
            salesOrderService.confirm(id);
            ra.addFlashAttribute("flashMessage", flash("so.confirmed", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("so.actionFailed", ex.getMessage()));
        }
        return "redirect:/sales/orders/" + id;
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id,
                         @RequestParam(value = "reason", required = false) String reason,
                         RedirectAttributes ra) {
        try {
            salesOrderService.cancel(id, reason);
            ra.addFlashAttribute("flashMessage", flash("so.cancelled", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("so.actionFailed", ex.getMessage()));
        }
        return "redirect:/sales/orders/" + id;
    }

    @PostMapping("/{id}/reopen")
    public String reopen(@PathVariable Long id, RedirectAttributes ra) {
        try {
            salesOrderService.reopen(id);
            ra.addFlashAttribute("flashMessage", flash("so.reopened", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("so.actionFailed", ex.getMessage()));
        }
        return "redirect:/sales/orders/" + id;
    }

    @PostMapping("/{id}/convert")
    public String convert(@PathVariable Long id, RedirectAttributes ra) {
        try {
            Invoice invoice = salesOrderService.convertToInvoice(id, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("so.converted", invoice.getInvoiceNo()));
            return "redirect:/invoices/" + invoice.getId();
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("so.actionFailed", ex.getMessage()));
            return "redirect:/sales/orders/" + id;
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            salesOrderService.delete(id);
            ra.addFlashAttribute("flashMessage", flash("so.deleted", null));
            return "redirect:/sales/orders";
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("so.actionFailed", ex.getMessage()));
            return "redirect:/sales/orders/" + id;
        }
    }
}
