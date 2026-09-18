/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : ContractorController.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Contractors web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.ContractorForm;
import com.ntaganira.heritier.ibook.entity.Company;
import com.ntaganira.heritier.ibook.entity.Contractor;
import com.ntaganira.heritier.ibook.enums.ContractorRateType;
import com.ntaganira.heritier.ibook.enums.ContractorType;
import com.ntaganira.heritier.ibook.repository.CompanyRepository;
import com.ntaganira.heritier.ibook.service.ContractorService;
import com.ntaganira.heritier.ibook.service.VendorService;
import jakarta.validation.Valid;
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
@RequestMapping("/purchases/contractors")
public class ContractorController {

    private static final int PAGE_SIZE = 25;

    private final ContractorService contractorService;
    private final VendorService vendorService;
    private final CompanyRepository companyRepository;
    private final MessageSource messageSource;

    public ContractorController(ContractorService contractorService,
                                VendorService vendorService,
                                CompanyRepository companyRepository,
                                MessageSource messageSource) {
        this.contractorService = contractorService;
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

    private Map<String, String> typeLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (ContractorType t : ContractorType.values()) {
            m.put(t.name(), msg("con.type." + t.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private Map<String, String> rateTypeLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (ContractorRateType t : ContractorRateType.values()) {
            m.put(t.name(), msg("con.rate." + t.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private void addFormContext(Model model, String mode, Long editingId) {
        model.addAttribute("mode", mode);
        model.addAttribute("editingId", editingId);
        model.addAttribute("typeLabels", typeLabels());
        model.addAttribute("rateTypeLabels", rateTypeLabels());
        model.addAttribute("vendors", vendorService.listActiveVendors());
        model.addAttribute("baseCurrency", baseCurrency());
    }

    // ----- List -----

    @GetMapping
    public String contractors(@RequestParam(value = "q", required = false) String q,
                              @RequestParam(value = "type", required = false) String type,
                              @RequestParam(value = "active", required = false) String active,
                              @RequestParam(value = "sort", defaultValue = "name") String sort,
                              @RequestParam(value = "dir", defaultValue = "asc") String dir,
                              @RequestParam(value = "page", defaultValue = "0") int page,
                              Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "name", "name", "trade", "contractorType",
                "contractEnd", "rateAmount", "active");
        model.addAttribute("contractors",
                contractorService.list(q, type, active, PageRequest.of(page, PAGE_SIZE, sp.sort())));
        model.addAttribute("summary", contractorService.summary());
        model.addAttribute("typeLabels", typeLabels());
        model.addAttribute("rateTypeLabels", rateTypeLabels());
        model.addAttribute("baseCurrency", baseCurrency());
        model.addAttribute("q", q);
        model.addAttribute("type", type == null ? "" : type);
        model.addAttribute("active", active == null ? "" : active);
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
        if (active != null && !active.isBlank()) {
            if (!fq.isEmpty()) {
                fq.append('&');
            }
            fq.append("active=").append(active);
        }
        SortSpec.addListContext(model, "/purchases/contractors", fq.isEmpty() ? "" : "?" + fq, sp);
        return "purchases/contractors";
    }

    // ----- View -----

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model, RedirectAttributes ra) {
        Contractor contractor = contractorService.get(id);
        if (contractor == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("con.notFound", null));
            return "redirect:/purchases/contractors";
        }
        model.addAttribute("contractor", contractor);
        model.addAttribute("spend", contractorService.spendFor(contractor));
        model.addAttribute("typeLabels", typeLabels());
        model.addAttribute("rateTypeLabels", rateTypeLabels());
        model.addAttribute("baseCurrency", baseCurrency());
        return "purchases/contractor-view";
    }

    // ----- Create / edit -----

    @GetMapping("/new")
    public String newContractor(Model model) {
        addFormContext(model, "create", null);
        model.addAttribute("form", ContractorForm.empty());
        return "purchases/contractor-form";
    }

    @GetMapping("/{id}/edit")
    public String editContractor(@PathVariable Long id, Model model, RedirectAttributes ra) {
        Contractor c = contractorService.get(id);
        if (c == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("con.notFound", null));
            return "redirect:/purchases/contractors";
        }
        addFormContext(model, "edit", id);
        model.addAttribute("form", new ContractorForm(
                c.getName(),
                c.getContractorType() == null ? null : c.getContractorType().name(),
                c.getTrade(), c.getEmail(), c.getPhone(), c.getMobileMoney(), c.getTaxId(),
                c.getStreet(), c.getCity(), c.getCountry(), c.getBankName(), c.getBankAccount(),
                c.getContractStart(), c.getContractEnd(),
                c.getRateType() == null ? null : c.getRateType().name(),
                c.getRateAmount(), c.getWithholdingRate(), c.getVendorId(), c.getNotes(),
                Boolean.valueOf(c.isActive())));
        return "purchases/contractor-form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") ContractorForm form,
                         BindingResult br, Model model, RedirectAttributes ra) {
        if (contractorService.nameExists(form.name(), null)) {
            br.rejectValue("name", "con.nameExists");
        }
        validateDates(form, br);
        if (br.hasErrors()) {
            addFormContext(model, "create", null);
            return "purchases/contractor-form";
        }
        Contractor saved = contractorService.save(form, null);
        ra.addFlashAttribute("flashMessage", flash("con.saved", saved.getName()));
        return "redirect:/purchases/contractors/" + saved.getId();
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") ContractorForm form,
                         BindingResult br, Model model, RedirectAttributes ra) {
        if (contractorService.get(id) == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("con.notFound", null));
            return "redirect:/purchases/contractors";
        }
        if (contractorService.nameExists(form.name(), id)) {
            br.rejectValue("name", "con.nameExists");
        }
        validateDates(form, br);
        if (br.hasErrors()) {
            addFormContext(model, "edit", id);
            return "purchases/contractor-form";
        }
        Contractor saved = contractorService.save(form, id);
        ra.addFlashAttribute("flashMessage", flash("con.saved", saved.getName()));
        return "redirect:/purchases/contractors/" + saved.getId();
    }

    private void validateDates(ContractorForm form, BindingResult br) {
        if (form.contractStart() != null && form.contractEnd() != null
                && form.contractEnd().isBefore(form.contractStart())) {
            br.rejectValue("contractEnd", "con.endBeforeStart");
        }
    }

    // ----- Lifecycle -----

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id, RedirectAttributes ra) {
        contractorService.toggle(id);
        Contractor contractor = contractorService.get(id);
        if (contractor != null) {
            ra.addFlashAttribute("flashMessage",
                    flash(contractor.isActive() ? "con.activated" : "con.deactivated", contractor.getName()));
        }
        return "redirect:/purchases/contractors/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        Contractor contractor = contractorService.get(id);
        String name = contractor == null ? null : contractor.getName();
        contractorService.delete(id);
        ra.addFlashAttribute("flashMessage", flash("con.deleted", name));
        return "redirect:/purchases/contractors";
    }
}
