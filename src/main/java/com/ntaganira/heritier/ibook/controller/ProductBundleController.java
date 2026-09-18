/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : ProductBundleController.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Product bundles web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.ProductBundleForm;
import com.ntaganira.heritier.ibook.entity.ProductBundle;
import com.ntaganira.heritier.ibook.entity.ProductBundleComponent;
import com.ntaganira.heritier.ibook.service.AuditService;
import com.ntaganira.heritier.ibook.service.InventoryService;
import com.ntaganira.heritier.ibook.service.ProductBundleService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.Map;

@Controller
@RequestMapping("/inventory/bundles")
public class ProductBundleController {

    private final ProductBundleService bundleService;
    private final InventoryService inventoryService;
    private final MessageSource messageSource;

    public ProductBundleController(ProductBundleService bundleService,
                                   InventoryService inventoryService,
                                   MessageSource messageSource) {
        this.bundleService = bundleService;
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

    /** A resolvable error code hides the default message, so the reason goes in as an argument. */
    private void rejectWithReason(BindingResult br, RuntimeException ex) {
        String reason = ex.getMessage() == null ? msg("inv.bnd.actionFailed") : ex.getMessage();
        br.reject("inv.bnd.failedDetail", new Object[]{reason}, reason);
    }

    private void addFormContext(Model model, String mode, Long editingId) {
        model.addAttribute("mode", mode);
        model.addAttribute("editingId", editingId);
        model.addAttribute("products", inventoryService.listStockedProducts());
        model.addAttribute("baseCurrency", bundleService.baseCurrency());
    }

    // ----- List -----

    @GetMapping
    public String bundles(@RequestParam(value = "q", required = false) String q, Model model) {
        model.addAttribute("bundles", bundleService.list(q));
        model.addAttribute("summary", bundleService.summary());
        model.addAttribute("baseCurrency", bundleService.baseCurrency());
        model.addAttribute("q", q);
        return "inventory/bundles";
    }

    // ----- Create / edit -----

    @GetMapping("/new")
    public String newBundle(Model model) {
        addFormContext(model, "create", null);
        model.addAttribute("form", ProductBundleForm.empty());
        return "inventory/bundle-form";
    }

    @GetMapping("/{id}/edit")
    public String editBundle(@PathVariable Long id, Model model, RedirectAttributes ra) {
        ProductBundle bundle = bundleService.get(id);
        if (bundle == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("inv.bnd.notFound", null));
            return "redirect:/inventory/bundles";
        }
        ProductBundleForm form = new ProductBundleForm();
        form.setCode(bundle.getCode());
        form.setName(bundle.getName());
        form.setDescription(bundle.getDescription());
        form.setBundleProductId(bundle.getBundleProductId());
        form.setActive(bundle.isActive());
        for (int i = 0; i < bundle.getComponents().size(); i++) {
            ProductBundleComponent src = bundle.getComponents().get(i);
            ProductBundleForm.Line line = form.getLines().get(i);
            line.setProductId(src.getProductId());
            line.setQuantity(src.getQuantity());
            line.setUnitCost(src.getUnitCost());
        }
        addFormContext(model, "edit", id);
        model.addAttribute("form", form);
        return "inventory/bundle-form";
    }

    @PostMapping
    public String create(@ModelAttribute("form") ProductBundleForm form, BindingResult br,
                         Model model, RedirectAttributes ra) {
        validate(form, br, null);
        if (br.hasErrors()) {
            addFormContext(model, "create", null);
            return "inventory/bundle-form";
        }
        try {
            ProductBundle saved = bundleService.save(form, null, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("inv.bnd.saved", saved.getName()));
            return "redirect:/inventory/bundles/" + saved.getId();
        } catch (RuntimeException ex) {
            rejectWithReason(br, ex);
            addFormContext(model, "create", null);
            return "inventory/bundle-form";
        }
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute("form") ProductBundleForm form,
                         BindingResult br, Model model, RedirectAttributes ra) {
        if (bundleService.get(id) == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("inv.bnd.notFound", null));
            return "redirect:/inventory/bundles";
        }
        validate(form, br, id);
        if (br.hasErrors()) {
            addFormContext(model, "edit", id);
            return "inventory/bundle-form";
        }
        try {
            ProductBundle saved = bundleService.save(form, id, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("inv.bnd.saved", saved.getName()));
            return "redirect:/inventory/bundles/" + saved.getId();
        } catch (RuntimeException ex) {
            rejectWithReason(br, ex);
            addFormContext(model, "edit", id);
            return "inventory/bundle-form";
        }
    }

    private void validate(ProductBundleForm form, BindingResult br, Long excludeId) {
        if (form.getCode() == null || form.getCode().isBlank()) {
            br.rejectValue("code", "inv.bnd.codeRequired");
        } else if (bundleService.codeExists(form.getCode(), excludeId)) {
            br.rejectValue("code", "inv.bnd.codeExists");
        }
        if (form.getName() == null || form.getName().isBlank()) {
            br.rejectValue("name", "inv.bnd.nameRequired");
        }
        if (form.getBundleProductId() == null) {
            br.rejectValue("bundleProductId", "inv.bnd.productRequired");
        } else if (bundleService.bundleProductTaken(form.getBundleProductId(), excludeId)) {
            br.rejectValue("bundleProductId", "inv.bnd.productTaken");
        }
        if (!form.hasComponents()) {
            br.rejectValue("lines", "inv.bnd.noComponents");
        }
    }

    // ----- View -----

    @GetMapping("/{id}")
    public String view(@PathVariable Long id,
                       @RequestParam(value = "warehouse", required = false) Long warehouseId,
                       Model model, RedirectAttributes ra) {
        ProductBundle bundle = bundleService.get(id);
        if (bundle == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("inv.bnd.notFound", null));
            return "redirect:/inventory/bundles";
        }
        Long resolved = warehouseId;
        if (resolved == null) {
            resolved = inventoryService.listActiveWarehouses().stream()
                    .filter(w -> w.isDefaultLocation()).map(w -> w.getId()).findFirst()
                    .orElseGet(() -> inventoryService.listActiveWarehouses().stream()
                            .map(w -> w.getId()).findFirst().orElse(null));
        }
        model.addAttribute("bundle", bundle);
        model.addAttribute("availability", bundleService.availability(bundle, resolved));
        model.addAttribute("movements", bundleService.movementsFor(bundle));
        model.addAttribute("warehouses", inventoryService.listActiveWarehouses());
        model.addAttribute("warehouseId", resolved);
        model.addAttribute("baseCurrency", bundleService.baseCurrency());
        return "inventory/bundle-view";
    }

    // ----- Assembly -----

    @PostMapping("/{id}/assemble")
    public String assemble(@PathVariable Long id,
                           @RequestParam(value = "warehouseId", required = false) Long warehouseId,
                           @RequestParam(value = "quantity", required = false) BigDecimal quantity,
                           RedirectAttributes ra) {
        try {
            ProductBundleService.AssemblyResult result =
                    bundleService.assemble(id, warehouseId, quantity, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("inv.bnd.assembled",
                    messageSource.getMessage("inv.bnd.assembledDetail",
                            new Object[]{result.quantity(), result.warehouseName()},
                            LocaleContextHolder.getLocale())));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("inv.bnd.actionFailed", ex.getMessage()));
        }
        return "redirect:/inventory/bundles/" + id
                + (warehouseId == null ? "" : "?warehouse=" + warehouseId);
    }

    @PostMapping("/{id}/disassemble")
    public String disassemble(@PathVariable Long id,
                              @RequestParam(value = "warehouseId", required = false) Long warehouseId,
                              @RequestParam(value = "quantity", required = false) BigDecimal quantity,
                              RedirectAttributes ra) {
        try {
            ProductBundleService.AssemblyResult result =
                    bundleService.disassemble(id, warehouseId, quantity, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("inv.bnd.disassembled",
                    messageSource.getMessage("inv.bnd.assembledDetail",
                            new Object[]{result.quantity(), result.warehouseName()},
                            LocaleContextHolder.getLocale())));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("inv.bnd.actionFailed", ex.getMessage()));
        }
        return "redirect:/inventory/bundles/" + id
                + (warehouseId == null ? "" : "?warehouse=" + warehouseId);
    }

    // ----- Lifecycle -----

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id, RedirectAttributes ra) {
        bundleService.toggle(id);
        ProductBundle bundle = bundleService.get(id);
        if (bundle != null) {
            ra.addFlashAttribute("flashMessage",
                    flash(bundle.isActive() ? "inv.bnd.activated" : "inv.bnd.deactivated",
                            bundle.getName()));
        }
        return "redirect:/inventory/bundles/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            ProductBundle bundle = bundleService.get(id);
            bundleService.delete(id);
            ra.addFlashAttribute("flashMessage",
                    flash("inv.bnd.deleted", bundle == null ? null : bundle.getName()));
            return "redirect:/inventory/bundles";
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("inv.bnd.actionFailed", ex.getMessage()));
            return "redirect:/inventory/bundles/" + id;
        }
    }
}
