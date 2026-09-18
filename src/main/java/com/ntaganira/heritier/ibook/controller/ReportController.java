/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : ReportController.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Financial statement reports web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.entity.Account;
import com.ntaganira.heritier.ibook.entity.Company;
import com.ntaganira.heritier.ibook.repository.CompanyRepository;
import com.ntaganira.heritier.ibook.service.AccountingService;
import com.ntaganira.heritier.ibook.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/reports")
public class ReportController {

    private final ReportService reportService;
    private final AccountingService accountingService;
    private final CompanyRepository companyRepository;

    public ReportController(ReportService reportService,
                            AccountingService accountingService,
                            CompanyRepository companyRepository) {
        this.reportService = reportService;
        this.accountingService = accountingService;
        this.companyRepository = companyRepository;
    }

    private String baseCurrency() {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElse(null);
        return company != null && company.getCurrencyCode() != null ? company.getCurrencyCode() : "RWF";
    }

    private LocalDate defaultFrom(LocalDate from) {
        return from != null ? from : LocalDate.now().withDayOfYear(1);
    }

    private LocalDate defaultTo(LocalDate to) {
        return to != null ? to : LocalDate.now();
    }

    private void addRange(Model model, LocalDate from, LocalDate to) {
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("baseCurrency", baseCurrency());
        model.addAttribute("company", companyRepository.findFirstByOrderByIdAsc().orElse(null));
    }

    // ----- Index -----

    @GetMapping
    public String index(@RequestParam(value = "from", required = false)
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                        @RequestParam(value = "to", required = false)
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                        Model model) {
        LocalDate start = defaultFrom(from);
        LocalDate end = defaultTo(to);
        addRange(model, start, end);
        model.addAttribute("pnl", reportService.profitAndLoss(start, end));
        model.addAttribute("balanceSheet", reportService.balanceSheet(end));
        model.addAttribute("cashFlow", reportService.cashFlow(start, end));
        model.addAttribute("accountCount", accountingService.listAccounts().size());
        model.addAttribute("postedEntries", accountingService.postedEntries());
        return "reports/index";
    }

    // ----- Profit and loss -----

    @GetMapping("/pnl")
    public String profitAndLoss(@RequestParam(value = "from", required = false)
                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam(value = "to", required = false)
                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                Model model) {
        LocalDate start = defaultFrom(from);
        LocalDate end = defaultTo(to);
        addRange(model, start, end);
        model.addAttribute("report", reportService.profitAndLoss(start, end));
        return "reports/pnl";
    }

    // ----- Balance sheet -----

    @GetMapping("/balance-sheet")
    public String balanceSheet(@RequestParam(value = "asOf", required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
                               Model model) {
        LocalDate date = defaultTo(asOf);
        model.addAttribute("asOf", date);
        model.addAttribute("baseCurrency", baseCurrency());
        model.addAttribute("company", companyRepository.findFirstByOrderByIdAsc().orElse(null));
        model.addAttribute("report", reportService.balanceSheet(date));
        return "reports/balance-sheet";
    }

    // ----- Cash flow -----

    @GetMapping("/cash-flow")
    public String cashFlow(@RequestParam(value = "from", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(value = "to", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           Model model) {
        LocalDate start = defaultFrom(from);
        LocalDate end = defaultTo(to);
        addRange(model, start, end);
        model.addAttribute("report", reportService.cashFlow(start, end));
        return "reports/cash-flow";
    }

    // ----- General ledger -----

    @GetMapping("/general-ledger")
    public String generalLedger(@RequestParam(value = "from", required = false)
                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam(value = "to", required = false)
                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                @RequestParam(value = "account", required = false) Long accountId,
                                Model model) {
        LocalDate start = defaultFrom(from);
        LocalDate end = defaultTo(to);
        addRange(model, start, end);
        List<Account> accounts = accountingService.listActiveAccounts();
        model.addAttribute("accounts", accounts);
        model.addAttribute("selectedAccount", accountId);
        model.addAttribute("sections", reportService.generalLedger(start, end, accountId));
        return "reports/general-ledger";
    }

    // ----- Trial balance (already implemented under /accounting) -----

    @GetMapping("/trial-balance")
    public String trialBalance() {
        return "redirect:/accounting/trial-balance";
    }
}
