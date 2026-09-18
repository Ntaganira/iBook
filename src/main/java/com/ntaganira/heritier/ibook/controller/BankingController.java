/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : BankingController.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Cash and bank account web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.entity.Company;
import com.ntaganira.heritier.ibook.repository.CompanyRepository;
import com.ntaganira.heritier.ibook.service.BankingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
@RequestMapping("/banking")
public class BankingController {

    private final BankingService bankingService;
    private final CompanyRepository companyRepository;

    public BankingController(BankingService bankingService, CompanyRepository companyRepository) {
        this.bankingService = bankingService;
        this.companyRepository = companyRepository;
    }

    private String baseCurrency() {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElse(null);
        return company != null && company.getCurrencyCode() != null ? company.getCurrencyCode() : "RWF";
    }

    @GetMapping
    public String index() {
        return "redirect:/banking/accounts";
    }

    @GetMapping("/accounts")
    public String accounts(Model model) {
        model.addAttribute("accounts", bankingService.accounts(null));
        model.addAttribute("totalBalance", bankingService.totalBalance(null));
        model.addAttribute("bankBalance", bankingService.totalBalance(BankingService.Kind.BANK));
        model.addAttribute("cashBalance", bankingService.totalBalance(BankingService.Kind.CASH));
        model.addAttribute("baseCurrency", baseCurrency());
        model.addAttribute("filter", "all");
        return "banking/accounts";
    }

    @GetMapping("/cash")
    public String cash(Model model) {
        model.addAttribute("accounts", bankingService.accounts(BankingService.Kind.CASH));
        model.addAttribute("totalBalance", bankingService.totalBalance(BankingService.Kind.CASH));
        model.addAttribute("bankBalance", bankingService.totalBalance(BankingService.Kind.BANK));
        model.addAttribute("cashBalance", bankingService.totalBalance(BankingService.Kind.CASH));
        model.addAttribute("baseCurrency", baseCurrency());
        model.addAttribute("filter", "cash");
        return "banking/accounts";
    }

    @GetMapping("/petty-cash")
    public String pettyCash() {
        return "redirect:/banking/cash";
    }

    @GetMapping("/transactions")
    public String transactions(@RequestParam(value = "from", required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(value = "to", required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                               @RequestParam(value = "account", required = false) Long accountId,
                               Model model) {
        LocalDate start = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate end = to != null ? to : LocalDate.now();
        model.addAttribute("from", start);
        model.addAttribute("to", end);
        model.addAttribute("selectedAccount", accountId);
        model.addAttribute("accounts", bankingService.accounts(null));
        model.addAttribute("rows", bankingService.transactions(start, end, accountId));
        model.addAttribute("baseCurrency", baseCurrency());
        return "banking/transactions";
    }
}
