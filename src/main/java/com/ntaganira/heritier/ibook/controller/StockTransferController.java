/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : StockTransferController.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Stock transfers web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.StockTransferForm;
import com.ntaganira.heritier.ibook.entity.StockTransfer;
import com.ntaganira.heritier.ibook.entity.StockTransferLine;
import com.ntaganira.heritier.ibook.enums.TransferStatus;
import com.ntaganira.heritier.ibook.service.AuditService;
import com.ntaganira.heritier.ibook.service.InventoryService;
import com.ntaganira.heritier.ibook.service.StockTransferService;
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
@RequestMapping("/inventory/transfers")
public class StockTransferController {

    private static final int PAGE_SIZE = 20;

    private final StockTransferService transferService;
    private final InventoryService inventoryService;
    private final MessageSource messageSource;

    public StockTransferController(StockTransferService transferService,
                                   InventoryService inventoryService,
                                   MessageSource messageSource) {
        this.transferService = transferService;
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
        Map<String, String> m = new LinkedHashMap<>();
        for (TransferStatus s : TransferStatus.values()) {
            m.put(s.name(), msg("inv.trf.status." + s.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private void addFormContext(Model model, String mode, Long editingId) {
        model.addAttribute("mode", mode);
        model.addAttribute("editingId", editingId);
        model.addAttribute("warehouses", inventoryService.listActiveWarehouses());
        model.addAttribute("products", inventoryService.listStockedProducts());
        model.addAttribute("onHand", transferService.onHandByWarehouse(java.time.LocalDate.now()));
        model.addAttribute("baseCurrency", transferService.baseCurrency());
        model.addAttribute("nextNumber", transferService.previewNextNumber());
    }

    // ----- List -----

    @GetMapping
    public String transfers(@RequestParam(value = "q", required = false) String q,
                            @RequestParam(value = "status", required = false) String status,
                            @RequestParam(value = "warehouse", required = false) Long warehouseId,
                            @RequestParam(value = "sort", defaultValue = "transferDate") String sort,
                            @RequestParam(value = "dir", defaultValue = "desc") String dir,
                            @RequestParam(value = "page", defaultValue = "0") int page,
                            Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "transferDate", "transferDate", "transferNo",
                "fromWarehouseName", "toWarehouseName", "totalValue", "status");
        model.addAttribute("transfers", transferService.list(q, status, warehouseId,
                PageRequest.of(page, PAGE_SIZE, sp.sort())));
        model.addAttribute("summary", transferService.summary());
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("baseCurrency", transferService.baseCurrency());
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
        SortSpec.addListContext(model, "/inventory/transfers", fq.isEmpty() ? "" : "?" + fq, sp);
        return "inventory/transfers";
    }

    // ----- Create / edit -----

    @GetMapping("/new")
    public String newTransfer(Model model) {
        addFormContext(model, "create", null);
        model.addAttribute("form", StockTransferForm.empty());
        return "inventory/transfer-form";
    }

    @GetMapping("/{id}/edit")
    public String editTransfer(@PathVariable Long id, Model model, RedirectAttributes ra) {
        StockTransfer transfer = transferService.get(id);
        if (transfer == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("inv.trf.notFound", null));
            return "redirect:/inventory/transfers";
        }
        if (!transfer.isEditable()) {
            ra.addFlashAttribute("flashMessage",
                    errorFlash("inv.trf.notEditable", transfer.getTransferNo()));
            return "redirect:/inventory/transfers/" + id;
        }
        StockTransferForm form = new StockTransferForm();
        form.setFromWarehouseId(transfer.getFromWarehouseId());
        form.setToWarehouseId(transfer.getToWarehouseId());
        form.setTransferDate(transfer.getTransferDate());
        form.setReference(transfer.getReference());
        form.setNotes(transfer.getNotes());
        for (int i = 0; i < transfer.getLines().size(); i++) {
            StockTransferLine src = transfer.getLines().get(i);
            StockTransferForm.Line line = form.getLines().get(i);
            line.setProductId(src.getProductId());
            line.setQuantitySent(src.getQuantitySent());
            line.setQuantityReceived(src.getQuantityReceived());
            line.setUnitCost(src.getUnitCost());
        }
        addFormContext(model, "edit", id);
        model.addAttribute("form", form);
        return "inventory/transfer-form";
    }

    @PostMapping
    public String create(@ModelAttribute("form") StockTransferForm form, BindingResult br,
                         Model model, RedirectAttributes ra) {
        validate(form, br);
        if (br.hasErrors()) {
            addFormContext(model, "create", null);
            return "inventory/transfer-form";
        }
        try {
            StockTransfer saved = transferService.save(form, null, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("inv.trf.saved", saved.getTransferNo()));
            return "redirect:/inventory/transfers/" + saved.getId();
        } catch (RuntimeException ex) {
            rejectWithReason(br, ex);
            addFormContext(model, "create", null);
            return "inventory/transfer-form";
        }
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute("form") StockTransferForm form,
                         BindingResult br, Model model, RedirectAttributes ra) {
        validate(form, br);
        if (br.hasErrors()) {
            addFormContext(model, "edit", id);
            return "inventory/transfer-form";
        }
        try {
            StockTransfer saved = transferService.save(form, id, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("inv.trf.saved", saved.getTransferNo()));
            return "redirect:/inventory/transfers/" + saved.getId();
        } catch (RuntimeException ex) {
            rejectWithReason(br, ex);
            addFormContext(model, "edit", id);
            return "inventory/transfer-form";
        }
    }

    /** A resolvable error code hides the default message, so the reason goes in as an argument. */
    private void rejectWithReason(BindingResult br, RuntimeException ex) {
        String reason = ex.getMessage() == null ? msg("inv.trf.actionFailed") : ex.getMessage();
        br.reject("inv.trf.failedDetail", new Object[]{reason}, reason);
    }

    private void validate(StockTransferForm form, BindingResult br) {
        if (form.getFromWarehouseId() == null) {
            br.rejectValue("fromWarehouseId", "inv.trf.fromRequired");
        }
        if (form.getToWarehouseId() == null) {
            br.rejectValue("toWarehouseId", "inv.trf.toRequired");
        }
        if (form.getFromWarehouseId() != null
                && form.getFromWarehouseId().equals(form.getToWarehouseId())) {
            br.rejectValue("toWarehouseId", "inv.trf.sameWarehouse");
        }
        if (form.getTransferDate() == null) {
            br.rejectValue("transferDate", "inv.trf.dateRequired");
        }
        if (!form.hasContent()) {
            br.rejectValue("lines", "inv.trf.noLines");
        }
    }

    // ----- View -----

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model, RedirectAttributes ra) {
        StockTransfer transfer = transferService.get(id);
        if (transfer == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("inv.trf.notFound", null));
            return "redirect:/inventory/transfers";
        }
        model.addAttribute("transfer", transfer);
        model.addAttribute("movements", transferService.movementsFor(transfer));
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("baseCurrency", transferService.baseCurrency());
        return "inventory/transfer-view";
    }

    // ----- Lifecycle -----

    @PostMapping("/{id}/complete")
    public String complete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            transferService.complete(id, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("inv.trf.completed", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("inv.trf.actionFailed", ex.getMessage()));
        }
        return "redirect:/inventory/transfers/" + id;
    }

    @PostMapping("/{id}/void")
    public String voidTransfer(@PathVariable Long id,
                               @RequestParam(value = "reason", required = false) String reason,
                               RedirectAttributes ra) {
        try {
            transferService.voidTransfer(id, reason, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("inv.trf.voided", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("inv.trf.actionFailed", ex.getMessage()));
        }
        return "redirect:/inventory/transfers/" + id;
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id,
                         @RequestParam(value = "reason", required = false) String reason,
                         RedirectAttributes ra) {
        try {
            transferService.cancel(id, reason);
            ra.addFlashAttribute("flashMessage", flash("inv.trf.cancelled", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("inv.trf.actionFailed", ex.getMessage()));
        }
        return "redirect:/inventory/transfers/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            transferService.delete(id);
            ra.addFlashAttribute("flashMessage", flash("inv.trf.deleted", null));
            return "redirect:/inventory/transfers";
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("inv.trf.actionFailed", ex.getMessage()));
            return "redirect:/inventory/transfers/" + id;
        }
    }
}
