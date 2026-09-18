/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : StockCountController.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Physical stock counts web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.StockCountForm;
import com.ntaganira.heritier.ibook.entity.StockCount;
import com.ntaganira.heritier.ibook.entity.StockCountLine;
import com.ntaganira.heritier.ibook.enums.StockCountStatus;
import com.ntaganira.heritier.ibook.service.AuditService;
import com.ntaganira.heritier.ibook.service.InventoryService;
import com.ntaganira.heritier.ibook.service.StockCountService;
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
@RequestMapping("/inventory/counts")
public class StockCountController {

    private static final int PAGE_SIZE = 20;

    private final StockCountService countService;
    private final InventoryService inventoryService;
    private final MessageSource messageSource;

    public StockCountController(StockCountService countService,
                                InventoryService inventoryService,
                                MessageSource messageSource) {
        this.countService = countService;
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
        for (StockCountStatus s : StockCountStatus.values()) {
            m.put(s.name(), msg("inv.cnt.status." + s.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private void addFormContext(Model model, String mode, Long editingId) {
        model.addAttribute("mode", mode);
        model.addAttribute("editingId", editingId);
        model.addAttribute("warehouses", inventoryService.listActiveWarehouses());
        model.addAttribute("baseCurrency", countService.baseCurrency());
        model.addAttribute("nextNumber", countService.previewNextNumber());
    }

    /** A resolvable error code hides the default message, so the reason goes in as an argument. */
    private void rejectWithReason(BindingResult br, RuntimeException ex) {
        String reason = ex.getMessage() == null ? msg("inv.cnt.actionFailed") : ex.getMessage();
        br.reject("inv.cnt.failedDetail", new Object[]{reason}, reason);
    }

    // ----- List -----

    @GetMapping
    public String counts(@RequestParam(value = "q", required = false) String q,
                         @RequestParam(value = "status", required = false) String status,
                         @RequestParam(value = "warehouse", required = false) Long warehouseId,
                         @RequestParam(value = "sort", defaultValue = "countDate") String sort,
                         @RequestParam(value = "dir", defaultValue = "desc") String dir,
                         @RequestParam(value = "page", defaultValue = "0") int page,
                         Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "countDate", "countDate", "countNo",
                "warehouseName", "countedBy", "linesCounted", "status");
        model.addAttribute("counts", countService.list(q, status, warehouseId,
                PageRequest.of(page, PAGE_SIZE, sp.sort())));
        model.addAttribute("summary", countService.summary());
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("baseCurrency", countService.baseCurrency());
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
        SortSpec.addListContext(model, "/inventory/counts", fq.isEmpty() ? "" : "?" + fq, sp);
        return "inventory/counts";
    }

    // ----- Create / edit -----

    @GetMapping("/new")
    public String newCount(@RequestParam(value = "warehouse", required = false) Long warehouseId,
                           Model model) {
        addFormContext(model, "create", null);
        model.addAttribute("form", countService.blankSheet(warehouseId));
        return "inventory/count-form";
    }

    @GetMapping("/{id}/edit")
    public String editCount(@PathVariable Long id, Model model, RedirectAttributes ra) {
        StockCount count = countService.get(id);
        if (count == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("inv.cnt.notFound", null));
            return "redirect:/inventory/counts";
        }
        if (!count.isEditable()) {
            ra.addFlashAttribute("flashMessage", errorFlash("inv.cnt.notEditable", count.getCountNo()));
            return "redirect:/inventory/counts/" + id;
        }
        // Start from a fresh sheet so items added since the draft was saved still appear, then
        // overlay what was already counted.
        StockCountForm form = countService.blankSheet(count.getWarehouseId());
        form.setCountDate(count.getCountDate());
        form.setCountedBy(count.getCountedBy());
        form.setReference(count.getReference());
        form.setNotes(count.getNotes());
        for (StockCountLine saved : count.getLines()) {
            for (StockCountForm.Line line : form.getLines()) {
                if (saved.getProductId().equals(line.getProductId())) {
                    line.setCountedQuantity(saved.getCountedQuantity());
                    line.setUnitCost(saved.getUnitCost());
                    break;
                }
            }
        }
        addFormContext(model, "edit", id);
        model.addAttribute("form", form);
        return "inventory/count-form";
    }

    @PostMapping
    public String create(@ModelAttribute("form") StockCountForm form, BindingResult br,
                         Model model, RedirectAttributes ra) {
        validate(form, br);
        if (br.hasErrors()) {
            addFormContext(model, "create", null);
            return "inventory/count-form";
        }
        try {
            StockCount saved = countService.save(form, null, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("inv.cnt.saved", saved.getCountNo()));
            return "redirect:/inventory/counts/" + saved.getId();
        } catch (RuntimeException ex) {
            rejectWithReason(br, ex);
            addFormContext(model, "create", null);
            return "inventory/count-form";
        }
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute("form") StockCountForm form,
                         BindingResult br, Model model, RedirectAttributes ra) {
        validate(form, br);
        if (br.hasErrors()) {
            addFormContext(model, "edit", id);
            return "inventory/count-form";
        }
        try {
            StockCount saved = countService.save(form, id, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("inv.cnt.saved", saved.getCountNo()));
            return "redirect:/inventory/counts/" + saved.getId();
        } catch (RuntimeException ex) {
            rejectWithReason(br, ex);
            addFormContext(model, "edit", id);
            return "inventory/count-form";
        }
    }

    private void validate(StockCountForm form, BindingResult br) {
        if (form.getWarehouseId() == null) {
            br.rejectValue("warehouseId", "inv.cnt.warehouseRequired");
        }
        if (form.getCountDate() == null) {
            br.rejectValue("countDate", "inv.cnt.dateRequired");
        }
        if (!form.hasCount()) {
            br.rejectValue("lines", "inv.cnt.nothingCounted");
        }
    }

    // ----- View -----

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model, RedirectAttributes ra) {
        StockCount count = countService.get(id);
        if (count == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("inv.cnt.notFound", null));
            return "redirect:/inventory/counts";
        }
        model.addAttribute("count", count);
        model.addAttribute("movements", countService.movementsFor(count));
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("baseCurrency", countService.baseCurrency());
        return "inventory/count-view";
    }

    // ----- Lifecycle -----

    @PostMapping("/{id}/complete")
    public String complete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            countService.complete(id, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("inv.cnt.completed", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("inv.cnt.actionFailed", ex.getMessage()));
        }
        return "redirect:/inventory/counts/" + id;
    }

    @PostMapping("/{id}/void")
    public String voidCount(@PathVariable Long id,
                            @RequestParam(value = "reason", required = false) String reason,
                            RedirectAttributes ra) {
        try {
            countService.voidCount(id, reason, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("inv.cnt.voided", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("inv.cnt.actionFailed", ex.getMessage()));
        }
        return "redirect:/inventory/counts/" + id;
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id,
                         @RequestParam(value = "reason", required = false) String reason,
                         RedirectAttributes ra) {
        try {
            countService.cancel(id, reason);
            ra.addFlashAttribute("flashMessage", flash("inv.cnt.cancelled", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("inv.cnt.actionFailed", ex.getMessage()));
        }
        return "redirect:/inventory/counts/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            countService.delete(id);
            ra.addFlashAttribute("flashMessage", flash("inv.cnt.deleted", null));
            return "redirect:/inventory/counts";
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("inv.cnt.actionFailed", ex.getMessage()));
            return "redirect:/inventory/counts/" + id;
        }
    }
}
