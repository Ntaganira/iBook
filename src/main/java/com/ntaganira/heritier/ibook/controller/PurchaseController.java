/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : PurchaseController.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Purchases reporting web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.entity.Company;
import com.ntaganira.heritier.ibook.enums.PaymentMethod;
import com.ntaganira.heritier.ibook.repository.CompanyRepository;
import com.ntaganira.heritier.ibook.service.BillService;
import com.ntaganira.heritier.ibook.service.VendorService;
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
@RequestMapping("/purchases")
public class PurchaseController {

    private final BillService billService;
    private final CompanyRepository companyRepository;
    private final VendorService vendorService;
    private final MessageSource messageSource;

    public PurchaseController(BillService billService,
                              CompanyRepository companyRepository,
                              VendorService vendorService,
                              MessageSource messageSource) {
        this.billService = billService;
        this.companyRepository = companyRepository;
        this.vendorService = vendorService;
        this.messageSource = messageSource;
    }

    private String baseCurrency() {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElse(null);
        return company != null && company.getCurrencyCode() != null ? company.getCurrencyCode() : "RWF";
    }

    @GetMapping("/aging")
    public String aging(@RequestParam(value = "asOf", required = false)
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
                        @RequestParam(value = "vendor", required = false) Long vendorId,
                        Model model) {
        LocalDate date = asOf != null ? asOf : LocalDate.now();
        model.addAttribute("asOf", date);
        model.addAttribute("report", billService.aging(date));
        model.addAttribute("baseCurrency", baseCurrency());
        model.addAttribute("company", companyRepository.findFirstByOrderByIdAsc().orElse(null));
        model.addAttribute("selectedVendor", vendorId);
        if (vendorId != null) {
            model.addAttribute("detail", billService.openBillsFor(vendorId, date));
        }
        return "purchases/aging";
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
        model.addAttribute("report", billService.paymentsMade(start, end, method));
        model.addAttribute("baseCurrency", baseCurrency());
        return "purchases/payments";
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
    public String statements(@RequestParam(value = "vendor", required = false) Long partyId,
                             @RequestParam(value = "from", required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(value = "to", required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                             Model model) {
        LocalDate start = from != null ? from : LocalDate.now().withDayOfYear(1);
        LocalDate end = to != null ? to : LocalDate.now();
        model.addAttribute("from", start);
        model.addAttribute("to", end);
        model.addAttribute("vendors", vendorService.listActiveVendors());
        model.addAttribute("selectedVendor", partyId);
        model.addAttribute("baseCurrency", baseCurrency());
        model.addAttribute("company", companyRepository.findFirstByOrderByIdAsc().orElse(null));
        if (partyId != null) {
            model.addAttribute("statement", billService.statementFor(partyId, start, end));
        }
        return "purchases/statements";
    }
}
