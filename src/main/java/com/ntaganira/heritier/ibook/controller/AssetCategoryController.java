/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : AssetCategoryController.java
 * - Date      : 2026. 09. 19.
 * - User      : Hntaganira
 * - Desc      : Fixed asset categories web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.AssetCategoryForm;
import com.ntaganira.heritier.ibook.entity.AssetCategory;
import com.ntaganira.heritier.ibook.enums.AccountType;
import com.ntaganira.heritier.ibook.enums.DepreciationMethod;
import com.ntaganira.heritier.ibook.service.AccountingService;
import com.ntaganira.heritier.ibook.service.AssetCategoryService;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/assets/categories")
public class AssetCategoryController {

    private final AssetCategoryService categoryService;
    private final AccountingService accountingService;
    private final MessageSource messageSource;

    public AssetCategoryController(AssetCategoryService categoryService,
                                   AccountingService accountingService,
                                   MessageSource messageSource) {
        this.categoryService = categoryService;
        this.accountingService = accountingService;
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

    private Map<String, String> methodLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (DepreciationMethod d : DepreciationMethod.values()) {
            m.put(d.name(), msg("ast.method." + d.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private void addListContext(Model model, String q, Long editingId) {
        model.addAttribute("categories", categoryService.list(q));
        model.addAttribute("counts", categoryService.assetCounts());
        model.addAttribute("summary", categoryService.summary());
        model.addAttribute("unlinked", categoryService.unlinkedNames());
        model.addAttribute("methodLabels", methodLabels());
        model.addAttribute("assetAccounts", accountingService.listActiveAccounts().stream()
                .filter(a -> a.getType() == AccountType.ASSET).toList());
        model.addAttribute("expenseAccounts", accountingService.listActiveAccounts().stream()
                .filter(a -> a.getType() == AccountType.EXPENSE).toList());
        model.addAttribute("q", q);
        model.addAttribute("editingId", editingId);
        model.addAttribute("mode", editingId == null ? "create" : "edit");
    }

    // ----- List, create and edit on one page -----

    @GetMapping
    public String categories(@RequestParam(value = "q", required = false) String q,
                             @RequestParam(value = "edit", required = false) Long editId,
                             Model model) {
        AssetCategory editing = editId == null ? null : categoryService.get(editId);
        addListContext(model, q, editing == null ? null : editing.getId());
        model.addAttribute("form", editing == null
                ? AssetCategoryForm.empty()
                : new AssetCategoryForm(editing.getName(), editing.getCode(),
                        editing.getDescription(), editing.getDepreciationMethod().name(),
                        Integer.valueOf(editing.getUsefulLifeYears()), editing.getDecliningRate(),
                        editing.getAssetAccountId(), editing.getAccumulatedAccountId(),
                        editing.getExpenseAccountId(), Boolean.valueOf(editing.isActive())));
        return "assets/categories";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") AssetCategoryForm form, BindingResult br,
                         Model model, RedirectAttributes ra) {
        validate(form, br, null);
        if (br.hasErrors()) {
            addListContext(model, null, null);
            return "assets/categories";
        }
        try {
            AssetCategory saved = categoryService.save(form, null);
            ra.addFlashAttribute("flashMessage", flash("ast.cat.saved", saved.getName()));
            return "redirect:/assets/categories";
        } catch (RuntimeException ex) {
            rejectWithReason(br, ex);
            addListContext(model, null, null);
            return "assets/categories";
        }
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") AssetCategoryForm form,
                         BindingResult br, Model model, RedirectAttributes ra) {
        if (categoryService.get(id) == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("ast.cat.notFound", null));
            return "redirect:/assets/categories";
        }
        validate(form, br, id);
        if (br.hasErrors()) {
            addListContext(model, null, id);
            return "assets/categories";
        }
        try {
            AssetCategory saved = categoryService.save(form, id);
            ra.addFlashAttribute("flashMessage", flash("ast.cat.saved", saved.getName()));
            return "redirect:/assets/categories";
        } catch (RuntimeException ex) {
            rejectWithReason(br, ex);
            addListContext(model, null, id);
            return "assets/categories";
        }
    }

    /** A resolvable error code hides the default message, so the reason goes in as an argument. */
    private void rejectWithReason(BindingResult br, RuntimeException ex) {
        String reason = ex.getMessage() == null ? msg("ast.cat.actionFailed") : ex.getMessage();
        br.reject("ast.cat.failedDetail", new Object[]{reason}, reason);
    }

    private void validate(AssetCategoryForm form, BindingResult br, Long excludeId) {
        if (categoryService.nameExists(form.name(), excludeId)) {
            br.rejectValue("name", "ast.cat.nameExists");
        }
        if (form.code() != null && !form.code().isBlank()
                && categoryService.codeExists(form.code(), excludeId)) {
            br.rejectValue("code", "ast.cat.codeExists");
        }
    }

    // ----- Lifecycle -----

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id, RedirectAttributes ra) {
        categoryService.toggle(id);
        AssetCategory category = categoryService.get(id);
        if (category != null) {
            ra.addFlashAttribute("flashMessage", flash(
                    category.isActive() ? "ast.cat.activated" : "ast.cat.deactivated",
                    category.getName()));
        }
        return "redirect:/assets/categories";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            AssetCategory category = categoryService.get(id);
            categoryService.delete(id);
            ra.addFlashAttribute("flashMessage",
                    flash("ast.cat.deleted", category == null ? null : category.getName()));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("ast.cat.actionFailed", ex.getMessage()));
        }
        return "redirect:/assets/categories";
    }

    @PostMapping("/import")
    public String importFromAssets(RedirectAttributes ra) {
        AssetCategoryService.ImportResult result = categoryService.importFromAssets();
        if (result.isEmpty()) {
            ra.addFlashAttribute("flashMessage", flash("ast.cat.importNothing", null));
        } else {
            ra.addFlashAttribute("flashMessage", flash("ast.cat.imported",
                    messageSource.getMessage("ast.cat.importDetail",
                            new Object[]{result.created(), result.linked()},
                            LocaleContextHolder.getLocale())));
        }
        return "redirect:/assets/categories";
    }
}
