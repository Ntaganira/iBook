/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : AssetLocationController.java
 * - Date      : 2026. 09. 19.
 * - User      : Hntaganira
 * - Desc      : Fixed asset locations web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.AssetLocationForm;
import com.ntaganira.heritier.ibook.entity.AssetLocation;
import com.ntaganira.heritier.ibook.service.AssetLocationService;
import com.ntaganira.heritier.ibook.service.FixedAssetService;
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
@RequestMapping("/assets/locations")
public class AssetLocationController {

    private final AssetLocationService locationService;
    private final FixedAssetService assetService;
    private final MessageSource messageSource;

    public AssetLocationController(AssetLocationService locationService,
                                   FixedAssetService assetService,
                                   MessageSource messageSource) {
        this.locationService = locationService;
        this.assetService = assetService;
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
        model.addAttribute("locations", locationService.list(q));
        model.addAttribute("counts", locationService.assetCounts());
        model.addAttribute("values", locationService.valueAtEach());
        model.addAttribute("summary", locationService.summary());
        model.addAttribute("unlinked", locationService.unlinkedNames());
        model.addAttribute("baseCurrency", assetService.baseCurrency());
        model.addAttribute("q", q);
        model.addAttribute("editingId", editingId);
        model.addAttribute("mode", editingId == null ? "create" : "edit");
    }

    // ----- List, create and edit on one page -----

    @GetMapping
    public String locations(@RequestParam(value = "q", required = false) String q,
                            @RequestParam(value = "edit", required = false) Long editId,
                            Model model) {
        AssetLocation editing = editId == null ? null : locationService.get(editId);
        addListContext(model, q, editing == null ? null : editing.getId());
        model.addAttribute("form", editing == null
                ? AssetLocationForm.empty()
                : new AssetLocationForm(editing.getName(), editing.getCode(),
                        editing.getDescription(), editing.getSite(), editing.getAddress(),
                        editing.getCity(), editing.getManager(), Boolean.valueOf(editing.isActive())));
        return "assets/locations";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") AssetLocationForm form, BindingResult br,
                         Model model, RedirectAttributes ra) {
        validate(form, br, null);
        if (br.hasErrors()) {
            addListContext(model, null, null);
            return "assets/locations";
        }
        AssetLocation saved = locationService.save(form, null);
        ra.addFlashAttribute("flashMessage", flash("ast.loc.saved", saved.getName()));
        return "redirect:/assets/locations";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") AssetLocationForm form,
                         BindingResult br, Model model, RedirectAttributes ra) {
        if (locationService.get(id) == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("ast.loc.notFound", null));
            return "redirect:/assets/locations";
        }
        validate(form, br, id);
        if (br.hasErrors()) {
            addListContext(model, null, id);
            return "assets/locations";
        }
        AssetLocation saved = locationService.save(form, id);
        ra.addFlashAttribute("flashMessage", flash("ast.loc.saved", saved.getName()));
        return "redirect:/assets/locations";
    }

    private void validate(AssetLocationForm form, BindingResult br, Long excludeId) {
        if (locationService.nameExists(form.name(), excludeId)) {
            br.rejectValue("name", "ast.loc.nameExists");
        }
        if (form.code() != null && !form.code().isBlank()
                && locationService.codeExists(form.code(), excludeId)) {
            br.rejectValue("code", "ast.loc.codeExists");
        }
    }

    // ----- Lifecycle -----

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id, RedirectAttributes ra) {
        locationService.toggle(id);
        AssetLocation location = locationService.get(id);
        if (location != null) {
            ra.addFlashAttribute("flashMessage", flash(
                    location.isActive() ? "ast.loc.activated" : "ast.loc.deactivated",
                    location.getName()));
        }
        return "redirect:/assets/locations";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            AssetLocation location = locationService.get(id);
            locationService.delete(id);
            ra.addFlashAttribute("flashMessage",
                    flash("ast.loc.deleted", location == null ? null : location.getName()));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("ast.loc.actionFailed", ex.getMessage()));
        }
        return "redirect:/assets/locations";
    }

    @PostMapping("/import")
    public String importFromAssets(RedirectAttributes ra) {
        AssetLocationService.ImportResult result = locationService.importFromAssets();
        if (result.isEmpty()) {
            ra.addFlashAttribute("flashMessage", flash("ast.loc.importNothing", null));
        } else {
            ra.addFlashAttribute("flashMessage", flash("ast.loc.imported",
                    messageSource.getMessage("ast.loc.importDetail",
                            new Object[]{result.created(), result.linked()},
                            LocaleContextHolder.getLocale())));
        }
        return "redirect:/assets/locations";
    }
}
