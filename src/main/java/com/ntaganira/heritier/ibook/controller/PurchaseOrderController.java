/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : PurchaseOrderController.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Vendor purchase orders web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.BillForm;
import com.ntaganira.heritier.ibook.dto.PurchaseOrderForm;
import com.ntaganira.heritier.ibook.entity.Account;
import com.ntaganira.heritier.ibook.entity.Bill;
import com.ntaganira.heritier.ibook.entity.PurchaseOrder;
import com.ntaganira.heritier.ibook.entity.PurchaseOrderLine;
import com.ntaganira.heritier.ibook.enums.AccountType;
import com.ntaganira.heritier.ibook.enums.PurchaseOrderStatus;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/purchases/orders")
public class PurchaseOrderController {

    private static final int PAGE_SIZE = 20;

    private final PurchaseOrderService purchaseOrderService;
    private final VendorService vendorService;
    private final AccountingService accountingService;
    private final InventoryService inventoryService;
    private final TaxRateService taxRateService;
    private final MessageSource messageSource;

    public PurchaseOrderController(PurchaseOrderService purchaseOrderService,
                                   VendorService vendorService,
                                   AccountingService accountingService,
                                   InventoryService inventoryService,
                                   TaxRateService taxRateService,
                                   MessageSource messageSource) {
        this.purchaseOrderService = purchaseOrderService;
        this.vendorService = vendorService;
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
        for (PurchaseOrderStatus s : PurchaseOrderStatus.values()) {
            m.put(s.name(), msg("po.status." + s.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private List<Account> expenseAccounts() {
        return accountingService.listActiveAccounts().stream()
                .filter(a -> a.getType() == AccountType.EXPENSE || a.getType() == AccountType.ASSET)
                .toList();
    }

    private void addFormContext(Model model, String mode, Long editingId) {
        model.addAttribute("mode", mode);
        model.addAttribute("editingId", editingId);
        model.addAttribute("vendors", vendorService.listActiveVendors());
        model.addAttribute("expenseAccounts", expenseAccounts());
        model.addAttribute("taxRates", taxRateService.listActive());
        model.addAttribute("products", inventoryService.listSellableProducts());
        model.addAttribute("baseCurrency", purchaseOrderService.baseCurrency());
        model.addAttribute("defaultVatRate", taxRateService.defaultRateValue());
        model.addAttribute("nextNumber", purchaseOrderService.previewNextNumber());
    }

    // ----- List -----

    @GetMapping
    public String orders(@RequestParam(value = "q", required = false) String q,
                         @RequestParam(value = "status", required = false) String status,
                         @RequestParam(value = "vendor", required = false) Long vendorId,
                         @RequestParam(value = "sort", defaultValue = "orderDate") String sort,
                         @RequestParam(value = "dir", defaultValue = "desc") String dir,
                         @RequestParam(value = "page", defaultValue = "0") int page,
                         Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "orderDate", "orderDate", "orderNo",
                "vendorName", "expectedDate", "total", "status");
        model.addAttribute("orders",
                purchaseOrderService.list(q, status, vendorId, PageRequest.of(page, PAGE_SIZE, sp.sort())));
        model.addAttribute("summary", purchaseOrderService.summary());
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("baseCurrency", purchaseOrderService.baseCurrency());
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
        SortSpec.addListContext(model, "/purchases/orders", fq.isEmpty() ? "" : "?" + fq, sp);
        return "purchases/orders";
    }

    // ----- Create / edit -----

    @GetMapping("/new")
    public String newOrder(@RequestParam(value = "vendor", required = false) Long vendorId,
                           Model model) {
        PurchaseOrderForm form = PurchaseOrderForm.empty(taxRateService.defaultRateValue());
        form.setCurrencyCode(purchaseOrderService.baseCurrency());
        if (vendorId != null) {
            form.setVendorId(vendorId);
        }
        addFormContext(model, "create", null);
        model.addAttribute("form", form);
        return "purchases/order-form";
    }

    @GetMapping("/{id}/edit")
    public String editOrder(@PathVariable Long id, Model model, RedirectAttributes ra) {
        PurchaseOrder order = purchaseOrderService.get(id);
        if (order == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("po.notFound", null));
            return "redirect:/purchases/orders";
        }
        if (!order.isEditable()) {
            ra.addFlashAttribute("flashMessage", errorFlash("po.notEditable", order.getOrderNo()));
            return "redirect:/purchases/orders/" + id;
        }
        PurchaseOrderForm form = new PurchaseOrderForm();
        form.setVendorId(order.getVendorId());
        form.setOrderDate(order.getOrderDate());
        form.setExpectedDate(order.getExpectedDate());
        form.setReference(order.getReference());
        form.setDeliveryAddress(order.getDeliveryAddress());
        form.setCurrencyCode(order.getCurrencyCode());
        form.setDiscountAmount(order.getDiscountAmount());
        form.setMemo(order.getMemo());
        form.setNotes(order.getNotes());
        for (int i = 0; i < order.getLines().size(); i++) {
            PurchaseOrderLine src = order.getLines().get(i);
            BillForm.Line line = form.getLines().get(i);
            line.setDescription(src.getDescription());
            line.setProductId(src.getProductId());
            line.setQuantity(src.getQuantity());
            line.setUnitPrice(src.getUnitPrice());
            line.setTaxRate(src.getTaxRate());
            line.setTaxRateId(src.getTaxRateId());
            line.setExpenseAccountId(src.getExpenseAccountId());
        }
        addFormContext(model, "edit", id);
        model.addAttribute("form", form);
        return "purchases/order-form";
    }

    @PostMapping
    public String create(@ModelAttribute("form") PurchaseOrderForm form, BindingResult br,
                         Model model, RedirectAttributes ra) {
        validate(form, br);
        if (br.hasErrors()) {
            addFormContext(model, "create", null);
            return "purchases/order-form";
        }
        PurchaseOrder saved = purchaseOrderService.save(form, null, AuditService.currentUsername());
        ra.addFlashAttribute("flashMessage", flash("po.saved", saved.getOrderNo()));
        return "redirect:/purchases/orders/" + saved.getId();
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute("form") PurchaseOrderForm form,
                         BindingResult br, Model model, RedirectAttributes ra) {
        validate(form, br);
        if (br.hasErrors()) {
            addFormContext(model, "edit", id);
            return "purchases/order-form";
        }
        try {
            PurchaseOrder saved = purchaseOrderService.save(form, id, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("po.saved", saved.getOrderNo()));
            return "redirect:/purchases/orders/" + saved.getId();
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("po.notEditable", null));
            return "redirect:/purchases/orders/" + id;
        }
    }

    private void validate(PurchaseOrderForm form, BindingResult br) {
        if (form.getVendorId() == null) {
            br.rejectValue("vendorId", "po.vendorRequired");
        }
        if (form.getOrderDate() == null) {
            br.rejectValue("orderDate", "po.dateRequired");
        }
        if (form.getOrderDate() != null && form.getExpectedDate() != null
                && form.getExpectedDate().isBefore(form.getOrderDate())) {
            br.rejectValue("expectedDate", "po.expectedBeforeDate");
        }
        if (!form.hasContent()) {
            br.rejectValue("lines", "po.noLines");
        }
    }

    // ----- View -----

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model, RedirectAttributes ra) {
        PurchaseOrder order = purchaseOrderService.get(id);
        if (order == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("po.notFound", null));
            return "redirect:/purchases/orders";
        }
        model.addAttribute("order", order);
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("baseCurrency", purchaseOrderService.baseCurrency());
        return "purchases/order-view";
    }

    // ----- Lifecycle -----

    @PostMapping("/{id}/confirm")
    public String confirm(@PathVariable Long id, RedirectAttributes ra) {
        try {
            purchaseOrderService.confirm(id);
            ra.addFlashAttribute("flashMessage", flash("po.confirmed", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("po.actionFailed", ex.getMessage()));
        }
        return "redirect:/purchases/orders/" + id;
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id,
                         @RequestParam(value = "reason", required = false) String reason,
                         RedirectAttributes ra) {
        try {
            purchaseOrderService.cancel(id, reason);
            ra.addFlashAttribute("flashMessage", flash("po.cancelled", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("po.actionFailed", ex.getMessage()));
        }
        return "redirect:/purchases/orders/" + id;
    }

    @PostMapping("/{id}/reopen")
    public String reopen(@PathVariable Long id, RedirectAttributes ra) {
        try {
            purchaseOrderService.reopen(id);
            ra.addFlashAttribute("flashMessage", flash("po.reopened", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("po.actionFailed", ex.getMessage()));
        }
        return "redirect:/purchases/orders/" + id;
    }

    @PostMapping("/{id}/convert")
    public String convert(@PathVariable Long id, RedirectAttributes ra) {
        try {
            Bill bill = purchaseOrderService.convertToBill(id, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("po.converted", bill.getBillNo()));
            return "redirect:/bills/" + bill.getId();
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("po.actionFailed", ex.getMessage()));
            return "redirect:/purchases/orders/" + id;
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            purchaseOrderService.delete(id);
            ra.addFlashAttribute("flashMessage", flash("po.deleted", null));
            return "redirect:/purchases/orders";
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("po.actionFailed", ex.getMessage()));
            return "redirect:/purchases/orders/" + id;
        }
    }
}
