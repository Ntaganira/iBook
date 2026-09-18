/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : CustomerController.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Customers web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.CustomerForm;
import com.ntaganira.heritier.ibook.entity.Company;
import com.ntaganira.heritier.ibook.entity.Customer;
import com.ntaganira.heritier.ibook.entity.Invoice;
import com.ntaganira.heritier.ibook.repository.CompanyRepository;
import com.ntaganira.heritier.ibook.service.CustomerService;
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
@RequestMapping("/customers")
public class CustomerController {

    private static final int PAGE_SIZE = 25;

    private final CustomerService customerService;
    private final CompanyRepository companyRepository;
    private final MessageSource messageSource;

    public CustomerController(CustomerService customerService,
                              CompanyRepository companyRepository,
                              MessageSource messageSource) {
        this.customerService = customerService;
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

    private void addFormContext(Model model, String mode, Long editingId) {
        model.addAttribute("mode", mode);
        model.addAttribute("editingId", editingId);
        model.addAttribute("baseCurrency", baseCurrency());
        model.addAttribute("paymentTermsOptions", paymentTermsOptions());
    }

    // ----- List -----

    @GetMapping
    public String customers(@RequestParam(value = "q", required = false) String q,
                            @RequestParam(value = "type", required = false) String type,
                            @RequestParam(value = "sort", defaultValue = "name") String sort,
                            @RequestParam(value = "dir", defaultValue = "asc") String dir,
                            @RequestParam(value = "page", defaultValue = "0") int page,
                            Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "name", "name", "companyName", "phone", "email", "city",
                "openingBalance", "active");
        Page<Customer> result = customerService.listCustomers(q, type, PageRequest.of(page, PAGE_SIZE, sp.sort()));
        model.addAttribute("customers", result);
        model.addAttribute("summary", customerService.summary());
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
        SortSpec.addListContext(model, "/customers", fq.isEmpty() ? "" : "?" + fq, sp);
        return "customers/list";
    }

    // ----- Detail -----

    @GetMapping("/{id}")
    public String viewCustomer(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Customer customer = customerService.getCustomer(id);
        if (customer == null) {
            redirectAttributes.addFlashAttribute("flashMessage", errorFlash("cust.notFound", null));
            return "redirect:/customers";
        }
        List<Invoice> invoices = customerService.invoicesFor(id);
        model.addAttribute("customer", customer);
        model.addAttribute("invoices", invoices);
        model.addAttribute("stats", customerService.statsFor(id));
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("baseCurrency", baseCurrency());
        return "customers/view";
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

    // ----- Create / edit -----

    @GetMapping("/new")
    public String newCustomer(Model model) {
        addFormContext(model, "create", null);
        model.addAttribute("form", CustomerForm.empty());
        return "customers/form";
    }

    @GetMapping("/{id}/edit")
    public String editCustomer(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Customer c = customerService.getCustomer(id);
        if (c == null) {
            redirectAttributes.addFlashAttribute("flashMessage", errorFlash("cust.notFound", null));
            return "redirect:/customers";
        }
        addFormContext(model, "edit", id);
        model.addAttribute("form", new CustomerForm(c.getName(), c.getCompanyName(), c.getEmail(), c.getPhone(),
                c.getMobileMoney(), c.getTaxId(), c.getStreet(), c.getCity(), c.getState(), c.getPostalCode(),
                c.getCountry(), c.getWebsite(), c.getNotes(), c.getPaymentTerms(), c.getOpeningBalance(),
                c.isActive()));
        return "customers/form";
    }

    @PostMapping
    public String createCustomer(@Valid @ModelAttribute("form") CustomerForm form,
                                 BindingResult bindingResult, Model model,
                                 RedirectAttributes redirectAttributes) {
        if (customerService.nameExists(form.name(), null)) {
            bindingResult.rejectValue("name", "cust.nameExists");
        }
        if (bindingResult.hasErrors()) {
            addFormContext(model, "create", null);
            return "customers/form";
        }
        Customer saved = customerService.saveCustomer(form, null);
        redirectAttributes.addFlashAttribute("flashMessage", flash("cust.saved", saved.getName()));
        return "redirect:/customers/" + saved.getId();
    }

    @PostMapping("/{id}")
    public String updateCustomer(@PathVariable Long id, @Valid @ModelAttribute("form") CustomerForm form,
                                 BindingResult bindingResult, Model model,
                                 RedirectAttributes redirectAttributes) {
        if (customerService.getCustomer(id) == null) {
            redirectAttributes.addFlashAttribute("flashMessage", errorFlash("cust.notFound", null));
            return "redirect:/customers";
        }
        if (customerService.nameExists(form.name(), id)) {
            bindingResult.rejectValue("name", "cust.nameExists");
        }
        if (bindingResult.hasErrors()) {
            addFormContext(model, "edit", id);
            return "customers/form";
        }
        Customer saved = customerService.saveCustomer(form, id);
        redirectAttributes.addFlashAttribute("flashMessage", flash("cust.saved", saved.getName()));
        return "redirect:/customers/" + saved.getId();
    }

    @PostMapping("/{id}/toggle")
    public String toggleCustomer(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        customerService.toggleCustomer(id);
        Customer customer = customerService.getCustomer(id);
        if (customer != null) {
            redirectAttributes.addFlashAttribute("flashMessage",
                    flash(customer.isActive() ? "cust.activated" : "cust.deactivated", customer.getName()));
        }
        return "redirect:/customers/" + id;
    }

    @PostMapping("/{id}/delete")
    public String deleteCustomer(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Customer customer = customerService.getCustomer(id);
        String name = customer == null ? null : customer.getName();
        try {
            customerService.deleteCustomer(id);
            redirectAttributes.addFlashAttribute("flashMessage", flash("cust.deleted", name));
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("flashMessage", errorFlash("cust.hasInvoices", name));
            return "redirect:/customers/" + id;
        }
        return "redirect:/customers";
    }
}
