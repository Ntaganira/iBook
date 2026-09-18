/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : VendorController.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Vendors (suppliers) web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.VendorForm;
import com.ntaganira.heritier.ibook.entity.Bill;
import com.ntaganira.heritier.ibook.entity.Company;
import com.ntaganira.heritier.ibook.entity.Vendor;
import com.ntaganira.heritier.ibook.repository.CompanyRepository;
import com.ntaganira.heritier.ibook.service.VendorService;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
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
import java.util.Map;

@Controller
@RequestMapping("/vendors")
public class VendorController {

    private static final int PAGE_SIZE = 25;

    private final VendorService vendorService;
    private final CompanyRepository companyRepository;
    private final MessageSource messageSource;

    public VendorController(VendorService vendorService,
                            CompanyRepository companyRepository,
                            MessageSource messageSource) {
        this.vendorService = vendorService;
        this.companyRepository = companyRepository;
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

    private String baseCurrency() {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElse(null);
        return company != null && company.getCurrencyCode() != null ? company.getCurrencyCode() : "RWF";
    }

    private Map<String, String> paymentTermsOptions() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("Due on receipt", msg("inv.terms.dueOnReceipt"));
        options.put("Net 7", msg("inv.terms.net7"));
        options.put("Net 15", msg("inv.terms.net15"));
        options.put("Net 30", msg("inv.terms.net30"));
        options.put("Net 60", msg("inv.terms.net60"));
        return options;
    }

    private Map<String, String> statusLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("DRAFT", msg("common.status.draft"));
        labels.put("OPEN", msg("common.status.open"));
        labels.put("PARTIALLY_PAID", msg("common.status.partiallyPaid"));
        labels.put("PAID", msg("common.status.paid"));
        labels.put("OVERDUE", msg("common.status.overdue"));
        labels.put("VOID", msg("common.status.void"));
        return labels;
    }

    private void addFormContext(Model model, String mode, Long editingId) {
        model.addAttribute("mode", mode);
        model.addAttribute("editingId", editingId);
        model.addAttribute("baseCurrency", baseCurrency());
        model.addAttribute("paymentTermsOptions", paymentTermsOptions());
    }

    @GetMapping
    public String vendors(@RequestParam(value = "q", required = false) String q,
                          @RequestParam(value = "type", required = false) String type,
                          @RequestParam(value = "sort", defaultValue = "name") String sort,
                          @RequestParam(value = "dir", defaultValue = "asc") String dir,
                          @RequestParam(value = "page", defaultValue = "0") int page,
                          Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "name", "name", "companyName", "phone", "email", "city",
                "openingBalance", "active");
        Page<Vendor> result = vendorService.listVendors(q, type, PageRequest.of(page, PAGE_SIZE, sp.sort()));
        model.addAttribute("vendors", result);
        model.addAttribute("summary", vendorService.summary());
        model.addAttribute("baseCurrency", baseCurrency());
        model.addAttribute("q", q);
        model.addAttribute("type", type == null ? "" : type);
        StringBuilder fq = new StringBuilder();
        if (q != null && !q.isBlank()) {
            fq.append("q=").append(UriUtils.encodeQueryParam(q, StandardCharsets.UTF_8));
        }
        if (type != null && !type.isBlank()) {
            if (!fq.isEmpty()) {
                fq.append('&');
            }
            fq.append("type=").append(type);
        }
        SortSpec.addListContext(model, "/vendors", fq.isEmpty() ? "" : "?" + fq, sp);
        return "vendors/list";
    }

    @GetMapping("/{id}")
    public String viewVendor(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Vendor vendor = vendorService.getVendor(id);
        if (vendor == null) {
            redirectAttributes.addFlashAttribute("flashMessage", errorFlash("ven.notFound", null));
            return "redirect:/vendors";
        }
        List<Bill> bills = vendorService.billsFor(id);
        model.addAttribute("vendor", vendor);
        model.addAttribute("bills", bills);
        model.addAttribute("stats", vendorService.statsFor(id));
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("baseCurrency", baseCurrency());
        return "vendors/view";
    }

    @GetMapping("/new")
    public String newVendor(Model model) {
        addFormContext(model, "create", null);
        model.addAttribute("form", VendorForm.empty());
        return "vendors/form";
    }

    @GetMapping("/{id}/edit")
    public String editVendor(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Vendor v = vendorService.getVendor(id);
        if (v == null) {
            redirectAttributes.addFlashAttribute("flashMessage", errorFlash("ven.notFound", null));
            return "redirect:/vendors";
        }
        addFormContext(model, "edit", id);
        model.addAttribute("form", new VendorForm(v.getName(), v.getCompanyName(), v.getEmail(), v.getPhone(),
                v.getMobileMoney(), v.getTaxId(), v.getStreet(), v.getCity(), v.getState(), v.getPostalCode(),
                v.getCountry(), v.getWebsite(), v.getNotes(), v.getPaymentTerms(), v.getOpeningBalance(),
                v.isActive()));
        return "vendors/form";
    }

    @PostMapping
    public String createVendor(@Valid @ModelAttribute("form") VendorForm form,
                               BindingResult bindingResult, Model model,
                               RedirectAttributes redirectAttributes) {
        if (vendorService.nameExists(form.name(), null)) {
            bindingResult.rejectValue("name", "ven.nameExists");
        }
        if (bindingResult.hasErrors()) {
            addFormContext(model, "create", null);
            return "vendors/form";
        }
        Vendor saved = vendorService.saveVendor(form, null);
        redirectAttributes.addFlashAttribute("flashMessage", flash("ven.saved", saved.getName()));
        return "redirect:/vendors/" + saved.getId();
    }

    @PostMapping("/{id}")
    public String updateVendor(@PathVariable Long id, @Valid @ModelAttribute("form") VendorForm form,
                               BindingResult bindingResult, Model model,
                               RedirectAttributes redirectAttributes) {
        if (vendorService.getVendor(id) == null) {
            redirectAttributes.addFlashAttribute("flashMessage", errorFlash("ven.notFound", null));
            return "redirect:/vendors";
        }
        if (vendorService.nameExists(form.name(), id)) {
            bindingResult.rejectValue("name", "ven.nameExists");
        }
        if (bindingResult.hasErrors()) {
            addFormContext(model, "edit", id);
            return "vendors/form";
        }
        Vendor saved = vendorService.saveVendor(form, id);
        redirectAttributes.addFlashAttribute("flashMessage", flash("ven.saved", saved.getName()));
        return "redirect:/vendors/" + saved.getId();
    }

    @PostMapping("/{id}/toggle")
    public String toggleVendor(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        vendorService.toggleVendor(id);
        Vendor vendor = vendorService.getVendor(id);
        if (vendor != null) {
            redirectAttributes.addFlashAttribute("flashMessage",
                    flash(vendor.isActive() ? "ven.activated" : "ven.deactivated", vendor.getName()));
        }
        return "redirect:/vendors/" + id;
    }

    @PostMapping("/{id}/delete")
    public String deleteVendor(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Vendor vendor = vendorService.getVendor(id);
        String name = vendor == null ? null : vendor.getName();
        try {
            vendorService.deleteVendor(id);
            redirectAttributes.addFlashAttribute("flashMessage", flash("ven.deleted", name));
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("flashMessage", errorFlash("ven.hasBills", name));
            return "redirect:/vendors/" + id;
        }
        return "redirect:/vendors";
    }
}
