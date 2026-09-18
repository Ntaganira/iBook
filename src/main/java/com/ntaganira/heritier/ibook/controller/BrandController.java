/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : BrandController.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Product brands web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.BrandForm;
import com.ntaganira.heritier.ibook.entity.Brand;
import com.ntaganira.heritier.ibook.service.BrandService;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/inventory/brands")
public class BrandController {

    private final BrandService brandService;
    private final MessageSource messageSource;

    public BrandController(BrandService brandService, MessageSource messageSource) {
        this.brandService = brandService;
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

    private void addListContext(Model model, String q, Long editingId) {
        model.addAttribute("brands", brandService.list(q));
        model.addAttribute("counts", brandService.productCounts());
        model.addAttribute("summary", brandService.summary());
        model.addAttribute("unlinked", brandService.unlinkedBrandNames());
        model.addAttribute("q", q);
        model.addAttribute("editingId", editingId);
        model.addAttribute("mode", editingId == null ? "create" : "edit");
    }

    // ----- List, create and edit on one page -----

    @GetMapping
    public String brands(@RequestParam(value = "q", required = false) String q,
                         @RequestParam(value = "edit", required = false) Long editId,
                         Model model) {
        Brand editing = editId == null ? null : brandService.get(editId);
        addListContext(model, q, editing == null ? null : editing.getId());
        model.addAttribute("form", editing == null
                ? BrandForm.empty()
                : new BrandForm(editing.getName(), editing.getCode(), editing.getDescription(),
                        editing.getManufacturer(), editing.getWebsite(),
                        Boolean.valueOf(editing.isActive())));
        return "inventory/brands";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") BrandForm form, BindingResult br,
                         Model model, RedirectAttributes ra) {
        validate(form, br, null);
        if (br.hasErrors()) {
            addListContext(model, null, null);
            return "inventory/brands";
        }
        Brand saved = brandService.save(form, null);
        ra.addFlashAttribute("flashMessage", flash("inv.brd.saved", saved.getName()));
        return "redirect:/inventory/brands";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") BrandForm form,
                         BindingResult br, Model model, RedirectAttributes ra) {
        if (brandService.get(id) == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("inv.brd.notFound", null));
            return "redirect:/inventory/brands";
        }
        validate(form, br, id);
        if (br.hasErrors()) {
            addListContext(model, null, id);
            return "inventory/brands";
        }
        Brand saved = brandService.save(form, id);
        ra.addFlashAttribute("flashMessage", flash("inv.brd.saved", saved.getName()));
        return "redirect:/inventory/brands";
    }

    private void validate(BrandForm form, BindingResult br, Long excludeId) {
        if (brandService.nameExists(form.name(), excludeId)) {
            br.rejectValue("name", "inv.brd.nameExists");
        }
        if (form.code() != null && !form.code().isBlank()
                && brandService.codeExists(form.code(), excludeId)) {
            br.rejectValue("code", "inv.brd.codeExists");
        }
    }

    // ----- Lifecycle -----

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id, RedirectAttributes ra) {
        brandService.toggle(id);
        Brand brand = brandService.get(id);
        if (brand != null) {
            ra.addFlashAttribute("flashMessage",
                    flash(brand.isActive() ? "inv.brd.activated" : "inv.brd.deactivated", brand.getName()));
        }
        return "redirect:/inventory/brands";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            Brand brand = brandService.get(id);
            brandService.delete(id);
            ra.addFlashAttribute("flashMessage",
                    flash("inv.brd.deleted", brand == null ? null : brand.getName()));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("inv.brd.actionFailed", ex.getMessage()));
        }
        return "redirect:/inventory/brands";
    }

    @PostMapping("/import")
    public String importFromProducts(RedirectAttributes ra) {
        BrandService.ImportResult result = brandService.importFromProducts();
        if (result.isEmpty()) {
            ra.addFlashAttribute("flashMessage", flash("inv.brd.importNothing", null));
        } else {
            ra.addFlashAttribute("flashMessage", flash("inv.brd.imported",
                    messageSource.getMessage("inv.brd.importDetail",
                            new Object[]{result.created(), result.linked()},
                            LocaleContextHolder.getLocale())));
        }
        return "redirect:/inventory/brands";
    }
}
