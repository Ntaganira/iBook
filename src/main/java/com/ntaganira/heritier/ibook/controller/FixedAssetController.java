/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : FixedAssetController.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Fixed asset register web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.FixedAssetForm;
import com.ntaganira.heritier.ibook.entity.FixedAsset;
import com.ntaganira.heritier.ibook.enums.AccountType;
import com.ntaganira.heritier.ibook.enums.AssetStatus;
import com.ntaganira.heritier.ibook.enums.DepreciationMethod;
import com.ntaganira.heritier.ibook.entity.AssetCategory;
import com.ntaganira.heritier.ibook.service.AccountingService;
import com.ntaganira.heritier.ibook.service.AssetCategoryService;
import com.ntaganira.heritier.ibook.service.AssetLocationService;
import com.ntaganira.heritier.ibook.service.AuditService;
import com.ntaganira.heritier.ibook.service.FixedAssetService;
import com.ntaganira.heritier.ibook.service.VendorService;
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
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/assets/register")
public class FixedAssetController {

    private static final int PAGE_SIZE = 20;

    private final FixedAssetService assetService;
    private final AccountingService accountingService;
    private final VendorService vendorService;
    private final AssetCategoryService assetCategoryService;
    private final AssetLocationService assetLocationService;
    private final MessageSource messageSource;

    public FixedAssetController(FixedAssetService assetService,
                                AccountingService accountingService,
                                VendorService vendorService,
                                AssetCategoryService assetCategoryService,
                                AssetLocationService assetLocationService,
                                MessageSource messageSource) {
        this.assetService = assetService;
        this.accountingService = accountingService;
        this.vendorService = vendorService;
        this.assetCategoryService = assetCategoryService;
        this.assetLocationService = assetLocationService;
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
        String reason = ex.getMessage() == null ? msg("ast.actionFailed") : ex.getMessage();
        br.reject("ast.failedDetail", new Object[]{reason}, reason);
    }

    private Map<String, String> statusLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (AssetStatus s : AssetStatus.values()) {
            m.put(s.name(), msg("ast.status." + s.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private Map<String, String> methodLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (DepreciationMethod d : DepreciationMethod.values()) {
            m.put(d.name(), msg("ast.method." + d.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private void addFormContext(Model model, String mode, Long editingId) {
        addFormContext(model, mode, editingId, null, null);
    }

    private void addFormContext(Model model, String mode, Long editingId,
                                Long currentCategoryId, Long currentLocationId) {
        model.addAttribute("mode", mode);
        model.addAttribute("editingId", editingId);
        model.addAttribute("methodLabels", methodLabels());
        model.addAttribute("vendors", vendorService.listActiveVendors());
        model.addAttribute("assetAccounts", accountingService.listActiveAccounts().stream()
                .filter(a -> a.getType() == AccountType.ASSET).toList());
        model.addAttribute("expenseAccounts", accountingService.listActiveAccounts().stream()
                .filter(a -> a.getType() == AccountType.EXPENSE).toList());
        model.addAttribute("assetCategories", assetCategoryService.listForPicker(currentCategoryId));
        model.addAttribute("assetLocations", assetLocationService.listForPicker(currentLocationId));
        model.addAttribute("baseCurrency", assetService.baseCurrency());
        model.addAttribute("nextNumber", assetService.previewNextNumber());
    }

    // ----- List -----

    @GetMapping
    public String register(@RequestParam(value = "q", required = false) String q,
                           @RequestParam(value = "status", required = false) String status,
                           @RequestParam(value = "category", required = false) String category,
                           @RequestParam(value = "sort", defaultValue = "assetNo") String sort,
                           @RequestParam(value = "dir", defaultValue = "asc") String dir,
                           @RequestParam(value = "page", defaultValue = "0") int page,
                           Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "assetNo", "assetNo", "name", "category",
                "acquisitionDate", "acquisitionCost", "status");
        model.addAttribute("assets", assetService.list(q, status, category,
                PageRequest.of(page, PAGE_SIZE, sp.sort())));
        model.addAttribute("summary", assetService.summary());
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("methodLabels", methodLabels());
        model.addAttribute("categories", assetService.categories());
        model.addAttribute("accountsMissing", assetService.accountsMissing());
        model.addAttribute("baseCurrency", assetService.baseCurrency());
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
        SortSpec.addListContext(model, "/assets/register", fq.isEmpty() ? "" : "?" + fq, sp);
        return "assets/register";
    }

    // ----- Create / edit -----

    /**
     * A category can be asked for by id, which fills the form with the depreciation policy and the
     * accounts that category carries. It is a visible action on the form rather than something that
     * happens on save, so what was taken from the category can be seen and overridden before saving.
     */
    @GetMapping("/new")
    public String newAsset(@RequestParam(value = "category", required = false) Long categoryId,
                           Model model) {
        addFormContext(model, "create", null);
        AssetCategory category = categoryId == null ? null : assetCategoryService.get(categoryId);
        model.addAttribute("form", category == null
                ? FixedAssetForm.empty()
                : FixedAssetForm.fromCategory(category.getId(),
                        category.getDepreciationMethod().name(), category.getUsefulLifeYears(),
                        category.getDecliningRate(), category.getAssetAccountId(),
                        category.getAccumulatedAccountId(), category.getExpenseAccountId()));
        model.addAttribute("appliedCategory", category);
        return "assets/asset-form";
    }

    @GetMapping("/{id}/edit")
    public String editAsset(@PathVariable Long id, Model model, RedirectAttributes ra) {
        FixedAsset asset = assetService.get(id);
        if (asset == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("ast.notFound", null));
            return "redirect:/assets/register";
        }
        if (!asset.isEditable()) {
            ra.addFlashAttribute("flashMessage", errorFlash("ast.notEditable", asset.getAssetNo()));
            return "redirect:/assets/register/" + id;
        }
        addFormContext(model, "edit", id, asset.getCategoryId(), asset.getLocationId());
        model.addAttribute("unlinkedCategory",
                asset.getCategoryId() == null ? asset.getCategory() : null);
        model.addAttribute("unlinkedLocation",
                asset.getLocationId() == null ? asset.getLocation() : null);
        model.addAttribute("form", new FixedAssetForm(
                asset.getName(), asset.getDescription(), asset.getCategoryId(), asset.getLocationId(),
                asset.getCustodian(), asset.getSerialNumber(), asset.getTagNumber(), asset.getVendorId(),
                asset.getPurchaseReference(), asset.getAcquisitionDate(), asset.getAcquisitionCost(),
                asset.getResidualValue(), asset.getDepreciationMethod().name(),
                asset.getUsefulLifeYears(), asset.getDecliningRate(), asset.getDepreciationStart(),
                asset.getOpeningAccumulated(), asset.getAssetAccountId(),
                asset.getAccumulatedAccountId(), asset.getExpenseAccountId(), asset.getNotes(),
                Boolean.FALSE));
        return "assets/asset-form";
    }

    @PostMapping
    public String create(@ModelAttribute("form") FixedAssetForm form, BindingResult br,
                         Model model, RedirectAttributes ra) {
        validate(form, br);
        if (br.hasErrors()) {
            addFormContext(model, "create", null, form.categoryId(), form.locationId());
            return "assets/asset-form";
        }
        try {
            FixedAsset saved = assetService.save(form, null, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("ast.saved", saved.getAssetNo()));
            return "redirect:/assets/register/" + saved.getId();
        } catch (RuntimeException ex) {
            rejectWithReason(br, ex);
            addFormContext(model, "create", null, form.categoryId(), form.locationId());
            return "assets/asset-form";
        }
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute("form") FixedAssetForm form,
                         BindingResult br, Model model, RedirectAttributes ra) {
        if (assetService.get(id) == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("ast.notFound", null));
            return "redirect:/assets/register";
        }
        validate(form, br);
        if (br.hasErrors()) {
            addFormContext(model, "edit", id, form.categoryId(), form.locationId());
            return "assets/asset-form";
        }
        try {
            FixedAsset saved = assetService.save(form, id, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("ast.saved", saved.getAssetNo()));
            return "redirect:/assets/register/" + saved.getId();
        } catch (RuntimeException ex) {
            rejectWithReason(br, ex);
            addFormContext(model, "edit", id, form.categoryId(), form.locationId());
            return "assets/asset-form";
        }
    }

    private void validate(FixedAssetForm form, BindingResult br) {
        if (form.name() == null || form.name().isBlank()) {
            br.rejectValue("name", "ast.nameRequired");
        }
        if (form.acquisitionDate() == null) {
            br.rejectValue("acquisitionDate", "ast.dateRequired");
        }
        if (form.costValue().signum() <= 0) {
            br.rejectValue("acquisitionCost", "ast.costRequired");
        }
        if (form.depreciationStart() != null && form.acquisitionDate() != null
                && form.depreciationStart().isBefore(form.acquisitionDate())) {
            br.rejectValue("depreciationStart", "ast.startBeforeAcquisition");
        }
    }

    // ----- View -----

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model, RedirectAttributes ra) {
        FixedAsset asset = assetService.get(id);
        if (asset == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("ast.notFound", null));
            return "redirect:/assets/register";
        }
        model.addAttribute("asset", asset);
        model.addAttribute("schedule", assetService.scheduleFor(asset));
        model.addAttribute("position", assetService.positionFor(asset, LocalDate.now()));
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("methodLabels", methodLabels());
        model.addAttribute("baseCurrency", assetService.baseCurrency());
        return "assets/asset-view";
    }

    // ----- Lifecycle -----

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id, RedirectAttributes ra) {
        try {
            assetService.activate(id);
            ra.addFlashAttribute("flashMessage", flash("ast.activated", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("ast.actionFailed", ex.getMessage()));
        }
        return "redirect:/assets/register/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            assetService.delete(id);
            ra.addFlashAttribute("flashMessage", flash("ast.deleted", null));
            return "redirect:/assets/register";
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("ast.actionFailed", ex.getMessage()));
            return "redirect:/assets/register/" + id;
        }
    }
}
