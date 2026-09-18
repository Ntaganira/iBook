/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : TaxController.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Taxes and compliance web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.TaxRateForm;
import com.ntaganira.heritier.ibook.entity.Company;
import com.ntaganira.heritier.ibook.entity.TaxRate;
import com.ntaganira.heritier.ibook.enums.TaxTreatment;
import com.ntaganira.heritier.ibook.repository.CompanyRepository;
import com.ntaganira.heritier.ibook.service.TaxRateService;
import com.ntaganira.heritier.ibook.service.TaxService;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/taxes")
public class TaxController {

    private final TaxService taxService;
    private final TaxRateService taxRateService;
    private final CompanyRepository companyRepository;
    private final MessageSource messageSource;

    public TaxController(TaxService taxService,
                         TaxRateService taxRateService,
                         CompanyRepository companyRepository,
                         MessageSource messageSource) {
        this.taxService = taxService;
        this.taxRateService = taxRateService;
        this.companyRepository = companyRepository;
        this.messageSource = messageSource;
    }

    private Company company() {
        return companyRepository.findFirstByOrderByIdAsc().orElse(null);
    }

    private String baseCurrency() {
        Company company = company();
        return company != null && company.getCurrencyCode() != null ? company.getCurrencyCode() : "RWF";
    }

    /** Default period is the current calendar month, matching the RRA monthly VAT cycle. */
    private LocalDate defaultFrom(LocalDate from) {
        return from != null ? from : LocalDate.now().withDayOfMonth(1);
    }

    private LocalDate defaultTo(LocalDate to) {
        return to != null ? to : LocalDate.now();
    }

    private void addContext(Model model, LocalDate from, LocalDate to) {
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("baseCurrency", baseCurrency());
        model.addAttribute("company", company());
        model.addAttribute("vatRate", taxRateService.defaultRateValue());
    }

    // ----- Index -----

    @GetMapping({"", "/reports"})
    public String index(@RequestParam(value = "from", required = false)
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                        @RequestParam(value = "to", required = false)
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                        Model model) {
        LocalDate start = defaultFrom(from);
        LocalDate end = defaultTo(to);
        addContext(model, start, end);
        model.addAttribute("vat", taxService.vatReturn(start, end));
        model.addAttribute("liability", taxService.taxLiability(end));
        return "taxes/index";
    }

    // ----- VAT return -----

    @GetMapping("/vat")
    public String vat(@RequestParam(value = "from", required = false)
                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                      @RequestParam(value = "to", required = false)
                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                      Model model) {
        LocalDate start = defaultFrom(from);
        LocalDate end = defaultTo(to);
        addContext(model, start, end);
        model.addAttribute("report", taxService.vatReturn(start, end));
        return "taxes/vat";
    }

    // ----- Tax liability -----

    @GetMapping("/liability")
    public String liability(@RequestParam(value = "asOf", required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
                            Model model) {
        LocalDate date = defaultTo(asOf);
        model.addAttribute("asOf", date);
        model.addAttribute("baseCurrency", baseCurrency());
        model.addAttribute("company", company());
        model.addAttribute("report", taxService.taxLiability(date));
        return "taxes/liability";
    }

    // ----- Tax transactions -----

    @GetMapping("/transactions")
    public String transactions(@RequestParam(value = "from", required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(value = "to", required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                               Model model) {
        LocalDate start = defaultFrom(from);
        LocalDate end = defaultTo(to);
        addContext(model, start, end);
        model.addAttribute("rows", taxService.taxTransactions(start, end));
        return "taxes/transactions";
    }

    // ----- Tax rate configuration -----

    @GetMapping("/config")
    public String config(Model model) {
        model.addAttribute("rates", taxRateService.listAll());
        model.addAttribute("treatmentLabels", treatmentLabels());
        model.addAttribute("defaultRate", taxRateService.defaultRate());
        model.addAttribute("baseCurrency", baseCurrency());
        return "taxes/config";
    }

    @GetMapping("/config/new")
    public String newRate(Model model) {
        model.addAttribute("mode", "create");
        model.addAttribute("editingId", null);
        model.addAttribute("treatmentLabels", treatmentLabels());
        model.addAttribute("form", TaxRateForm.empty());
        return "taxes/config-form";
    }

    @GetMapping("/config/{id}/edit")
    public String editRate(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        TaxRate rate = taxRateService.get(id);
        if (rate == null) {
            redirectAttributes.addFlashAttribute("flashMessage", errorFlash("tax.cfg.notFound", null));
            return "redirect:/taxes/config";
        }
        model.addAttribute("mode", "edit");
        model.addAttribute("editingId", id);
        model.addAttribute("treatmentLabels", treatmentLabels());
        model.addAttribute("form", new TaxRateForm(rate.getCode(), rate.getName(), rate.getRate(),
                rate.getTreatment().name(), rate.getDescription(), rate.isDefaultRate(), rate.isActive()));
        return "taxes/config-form";
    }

    @PostMapping("/config")
    public String createRate(@Valid @ModelAttribute("form") TaxRateForm form, BindingResult bindingResult,
                             Model model, RedirectAttributes redirectAttributes) {
        if (taxRateService.codeExists(form.code(), null)) {
            bindingResult.rejectValue("code", "tax.cfg.codeExists");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("mode", "create");
            model.addAttribute("editingId", null);
            model.addAttribute("treatmentLabels", treatmentLabels());
            return "taxes/config-form";
        }
        TaxRate saved = taxRateService.save(form, null);
        redirectAttributes.addFlashAttribute("flashMessage", flash("tax.cfg.saved", saved.getLabel()));
        return "redirect:/taxes/config";
    }

    @PostMapping("/config/{id}")
    public String updateRate(@PathVariable Long id, @Valid @ModelAttribute("form") TaxRateForm form,
                             BindingResult bindingResult, Model model,
                             RedirectAttributes redirectAttributes) {
        if (taxRateService.get(id) == null) {
            redirectAttributes.addFlashAttribute("flashMessage", errorFlash("tax.cfg.notFound", null));
            return "redirect:/taxes/config";
        }
        if (taxRateService.codeExists(form.code(), id)) {
            bindingResult.rejectValue("code", "tax.cfg.codeExists");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("mode", "edit");
            model.addAttribute("editingId", id);
            model.addAttribute("treatmentLabels", treatmentLabels());
            return "taxes/config-form";
        }
        TaxRate saved = taxRateService.save(form, id);
        redirectAttributes.addFlashAttribute("flashMessage", flash("tax.cfg.saved", saved.getLabel()));
        return "redirect:/taxes/config";
    }

    @PostMapping("/config/{id}/default")
    public String makeDefault(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        taxRateService.makeDefault(id);
        redirectAttributes.addFlashAttribute("flashMessage", flash("tax.cfg.defaultSet", null));
        return "redirect:/taxes/config";
    }

    @PostMapping("/config/{id}/toggle")
    public String toggleRate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        taxRateService.toggle(id);
        redirectAttributes.addFlashAttribute("flashMessage", flash("tax.cfg.saved", null));
        return "redirect:/taxes/config";
    }

    private Map<String, String> treatmentLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        for (TaxTreatment tr : TaxTreatment.values()) {
            labels.put(tr.name(), msg("tax.cfg.treatment." + tr.name().toLowerCase(Locale.ROOT)));
        }
        return labels;
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
}
