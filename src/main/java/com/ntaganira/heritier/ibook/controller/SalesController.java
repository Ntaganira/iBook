/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : SalesController.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Sales reporting web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.entity.Company;
import com.ntaganira.heritier.ibook.enums.PaymentMethod;
import com.ntaganira.heritier.ibook.repository.CompanyRepository;
import com.ntaganira.heritier.ibook.service.InvoiceService;
import com.ntaganira.heritier.ibook.service.CustomerService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/sales")
public class SalesController {

    private final InvoiceService invoiceService;
    private final CompanyRepository companyRepository;
    private final CustomerService customerService;
    private final MessageSource messageSource;

    public SalesController(InvoiceService invoiceService,
                           CompanyRepository companyRepository,
                           CustomerService customerService,
                           MessageSource messageSource) {
        this.invoiceService = invoiceService;
        this.companyRepository = companyRepository;
        this.customerService = customerService;
        this.messageSource = messageSource;
    }

    private String baseCurrency() {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElse(null);
        return company != null && company.getCurrencyCode() != null ? company.getCurrencyCode() : "RWF";
    }

    @GetMapping("/aging")
    public String aging(@RequestParam(value = "asOf", required = false)
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
                        @RequestParam(value = "customer", required = false) Long customerId,
                        Model model) {
        LocalDate date = asOf != null ? asOf : LocalDate.now();
        model.addAttribute("asOf", date);
        model.addAttribute("report", invoiceService.aging(date));
        model.addAttribute("baseCurrency", baseCurrency());
        model.addAttribute("company", companyRepository.findFirstByOrderByIdAsc().orElse(null));
        model.addAttribute("selectedCustomer", customerId);
        if (customerId != null) {
            model.addAttribute("detail", invoiceService.openInvoicesFor(customerId, date));
        }
        return "sales/aging";
    }

    @GetMapping("/payments")
    public String payments(@RequestParam(value = "from", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(value = "to", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           @RequestParam(value = "method", required = false) String method,
                           Model model) {
        LocalDate start = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate end = to != null ? to : LocalDate.now();
        model.addAttribute("from", start);
        model.addAttribute("to", end);
        model.addAttribute("method", method == null ? "" : method);
        model.addAttribute("methodLabels", methodLabels());
        model.addAttribute("report", invoiceService.paymentsReceived(start, end, method));
        model.addAttribute("baseCurrency", baseCurrency());
        return "sales/payments";
    }

    private Map<String, String> methodLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(PaymentMethod.BANK_TRANSFER.name(), msg("inv.pay.bankTransfer"));
        labels.put(PaymentMethod.CASH.name(), msg("inv.pay.cash"));
        labels.put(PaymentMethod.MOBILE_MONEY.name(), msg("inv.pay.mtnMobileMoney"));
        labels.put(PaymentMethod.CARD.name(), msg("inv.pay.card"));
        labels.put(PaymentMethod.CHECK.name(), msg("inv.pay.check"));
        return labels;
    }

    private String msg(String key) {
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
    }

    @GetMapping("/statements")
    public String statements(@RequestParam(value = "customer", required = false) Long partyId,
                             @RequestParam(value = "from", required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(value = "to", required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                             Model model) {
        LocalDate start = from != null ? from : LocalDate.now().withDayOfYear(1);
        LocalDate end = to != null ? to : LocalDate.now();
        model.addAttribute("from", start);
        model.addAttribute("to", end);
        model.addAttribute("customers", customerService.listActiveCustomers());
        model.addAttribute("selectedCustomer", partyId);
        model.addAttribute("baseCurrency", baseCurrency());
        model.addAttribute("company", companyRepository.findFirstByOrderByIdAsc().orElse(null));
        if (partyId != null) {
            model.addAttribute("statement", invoiceService.statementFor(partyId, start, end));
        }
        return "sales/statements";
    }

    @GetMapping("/collections")
    public String collections(@RequestParam(value = "asOf", required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
                              @RequestParam(value = "minDays", defaultValue = "1") int minDays,
                              @RequestParam(value = "customer", required = false) Long customerId,
                              Model model) {
        LocalDate date = asOf != null ? asOf : LocalDate.now();
        model.addAttribute("asOf", date);
        model.addAttribute("minDays", minDays);
        model.addAttribute("customers", customerService.listActiveCustomers());
        model.addAttribute("selectedCustomer", customerId);
        model.addAttribute("report", invoiceService.collections(date, minDays, customerId));
        model.addAttribute("baseCurrency", baseCurrency());
        return "sales/collections";
    }
}
