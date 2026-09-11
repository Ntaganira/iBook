/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : AccountingController.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : Chart of accounts, journals, ledger and periods web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.AccountForm;
import com.ntaganira.heritier.ibook.dto.JournalEntryForm;
import com.ntaganira.heritier.ibook.entity.*;
import com.ntaganira.heritier.ibook.enums.AccountType;
import com.ntaganira.heritier.ibook.service.AccountingService;
import com.ntaganira.heritier.ibook.service.AuditService;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class AccountingController {

    private static final int PAGE_SIZE = 20;

    private final AccountingService accountingService;
    private final MessageSource messageSource;

    public AccountingController(AccountingService accountingService, MessageSource messageSource) {
        this.accountingService = accountingService;
        this.messageSource = messageSource;
    }

    private String msg(String key) {
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
    }

    private Map<String, String> flash(String key, String detail) {
        return Map.of("title", msg(key), "detail", detail == null ? "" : detail, "type", "success");
    }

    // ----- Chart of accounts -----

    @GetMapping("/accounts")
    public String chartOfAccounts(@RequestParam(value = "q", required = false) String q,
                                  @RequestParam(value = "type", required = false) String type,
                                  Model model) {
        List<Account> accounts = accountingService.listAccounts(q, type);
        Map<Long, Account> byId = new LinkedHashMap<>();
        for (Account a : accounts) {
            byId.put(a.getId(), a);
        }
        Map<Long, BigDecimal> balances = accountingService.accountBalances();

        List<TypeSection> sections = new ArrayList<>();
        for (AccountType accountType : AccountType.values()) {
            List<Account> members = accounts.stream()
                    .filter(a -> a.getType() == accountType)
                    .toList();
            if (members.isEmpty()) {
                continue;
            }
            BigDecimal total = BigDecimal.ZERO;
            for (Account a : members) {
                BigDecimal balance = balances.getOrDefault(a.getId(), zero(a.getOpeningBalance()));
                total = total.add(balance);
            }
            sections.add(new TypeSection(msg("common.type." + accountType.name().toLowerCase(LocaleContextHolder.getLocale())),
                    members, total));
        }

        Map<Long, String> parentLabels = new LinkedHashMap<>();
        for (Account a : accounts) {
            if (a.getParentId() != null) {
                Account parent = byId.get(a.getParentId());
                if (parent != null) {
                    parentLabels.put(a.getId(), parent.getCode() + " " + parent.getName());
                }
            }
        }

        model.addAttribute("sections", sections);
        model.addAttribute("balances", balances);
        model.addAttribute("parentLabels", parentLabels);
        model.addAttribute("typeLabels", typeLabels());
        model.addAttribute("q", q);
        model.addAttribute("type", type == null ? "" : type);
        model.addAttribute("baseCurrency", baseCurrency());
        model.addAttribute("totalAccounts", accounts.size());
        return "accounts/list";
    }

    @GetMapping("/accounts/new")
    public String newAccount(Model model) {
        model.addAttribute("mode", "create");
        model.addAttribute("editingId", null);
        model.addAttribute("parents", accountingService.listAccounts());
        model.addAttribute("typeLabels", typeLabels());
        model.addAttribute("form", new AccountForm("", "", "ASSET", null, null, BigDecimal.ZERO, true));
        return "accounts/form";
    }

    @GetMapping("/accounts/{id}/edit")
    public String editAccount(@PathVariable Long id, Model model) {
        Account a = accountingService.getAccount(id);
        if (a == null) {
            return "redirect:/accounts";
        }
        model.addAttribute("mode", "edit");
        model.addAttribute("editingId", id);
        model.addAttribute("parents", accountingService.listAccounts().stream()
                .filter(p -> !p.getId().equals(id)).toList());
        model.addAttribute("typeLabels", typeLabels());
        model.addAttribute("form", new AccountForm(a.getCode(), a.getName(), a.getType().name(),
                a.getParentId(), a.getDescription(), a.getOpeningBalance(), a.isActive()));
        return "accounts/form";
    }

    @PostMapping("/accounts")
    public String createAccount(@Valid @ModelAttribute("form") AccountForm form,
                                BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (accountingService.accountCodeExists(form.code(), null)) {
            bindingResult.rejectValue("code", "acc.code.exists");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("mode", "create");
            model.addAttribute("editingId", null);
            model.addAttribute("parents", accountingService.listAccounts());
            model.addAttribute("typeLabels", typeLabels());
            return "accounts/form";
        }
        Account saved = accountingService.saveAccount(form, null);
        redirectAttributes.addFlashAttribute("flashMessage", flash("acc.saved", saved.getCode() + " — " + saved.getName()));
        return "redirect:/accounts";
    }

    @PostMapping("/accounts/{id}")
    public String updateAccount(@PathVariable Long id, @Valid @ModelAttribute("form") AccountForm form,
                                BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (accountingService.accountCodeExists(form.code(), id)) {
            bindingResult.rejectValue("code", "acc.code.exists");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("mode", "edit");
            model.addAttribute("editingId", id);
            model.addAttribute("parents", accountingService.listAccounts().stream()
                    .filter(p -> !p.getId().equals(id)).toList());
            model.addAttribute("typeLabels", typeLabels());
            return "accounts/form";
        }
        Account saved = accountingService.saveAccount(form, id);
        redirectAttributes.addFlashAttribute("flashMessage", flash("acc.saved", saved.getCode() + " — " + saved.getName()));
        return "redirect:/accounts";
    }

    @PostMapping("/accounts/{id}/toggle")
    public String toggleAccount(@PathVariable Long id) {
        accountingService.toggleAccount(id);
        return "redirect:/accounts";
    }

    // ----- Journal entries -----

    @GetMapping("/journals")
    public String journals(@RequestParam(value = "q", required = false) String q,
                           @RequestParam(value = "status", required = false) String status,
                           @RequestParam(value = "sort", defaultValue = "entryDate") String sort,
                           @RequestParam(value = "dir", defaultValue = "desc") String dir,
                           @RequestParam(value = "page", defaultValue = "0") int page,
                           Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "entryDate", "entryDate", "entryNo", "reference", "status");
        Page<JournalEntry> result = accountingService.listJournalEntries(q, status, PageRequest.of(page, PAGE_SIZE, sp.sort()));
        model.addAttribute("entries", result);
        model.addAttribute("q", q);
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("baseCurrency", baseCurrency());
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
        SortSpec.addListContext(model, "/journals", fq.isEmpty() ? "" : "?" + fq, sp);
        return "journals/list";
    }

    @GetMapping("/journals/new")
    public String newJournalEntry(Model model) {
        model.addAttribute("mode", "create");
        model.addAttribute("editingId", null);
        model.addAttribute("accounts", accountingService.listActiveAccounts());
        model.addAttribute("baseCurrency", baseCurrency());
        model.addAttribute("form", JournalEntryForm.empty());
        return "journals/form";
    }

    @GetMapping("/journals/{id}")
    public String viewJournalEntry(@PathVariable Long id, Model model) {
        JournalEntry entry = accountingService.getJournalEntry(id);
        if (entry == null) {
            return "redirect:/journals";
        }
        model.addAttribute("entry", entry);
        model.addAttribute("baseCurrency", baseCurrency());
        return "journals/view";
    }

    @PostMapping("/journals")
    public String createJournalEntry(@ModelAttribute("form") JournalEntryForm form,
                                     BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (form.getEntryDate() == null) {
            bindingResult.rejectValue("entryDate", "jr.dateRequired");
        }
        if (!form.hasContent()) {
            bindingResult.rejectValue("lines", "jr.noLines");
        }
        if (form.hasContent() && !form.balanced()) {
            bindingResult.rejectValue("lines", "jr.notBalanced");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("mode", "create");
            model.addAttribute("editingId", null);
            model.addAttribute("accounts", accountingService.listActiveAccounts());
            model.addAttribute("baseCurrency", baseCurrency());
            return "journals/form";
        }
        JournalEntry saved = accountingService.createJournalEntry(form, AuditService.currentUsername());
        redirectAttributes.addFlashAttribute("flashMessage", flash("jr.saved", saved.getEntryNo()));
        return "redirect:/journals";
    }

    // ----- Ledger -----

    @GetMapping("/accounting/ledger")
    public String ledger(@RequestParam(value = "account", required = false) Long accountId,
                         @RequestParam(value = "from", required = false) LocalDate from,
                         @RequestParam(value = "to", required = false) LocalDate to,
                         Model model) {
        List<Account> accounts = accountingService.listActiveAccounts();
        Account selected = accountId == null ? null : accountingService.getAccount(accountId);
        List<LedgerRow> rows = new ArrayList<>();
        BigDecimal opening = BigDecimal.ZERO;
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;

        if (selected != null) {
            opening = zero(selected.getOpeningBalance());
            BigDecimal running = opening;
            for (JournalLine line : accountingService.postedLinesForAccount(selected.getId())) {
                if (withinRange(line.getEntry().getEntryDate(), from, to)) {
                    BigDecimal debit = line.getDebitValue();
                    BigDecimal credit = line.getCreditValue();
                    totalDebits = totalDebits.add(debit);
                    totalCredits = totalCredits.add(credit);
                    running = running.add(netAmount(selected.getType(), debit, credit));
                    rows.add(new LedgerRow(line.getEntry().getEntryDate(), line.getEntry().getEntryNo(),
                            line.getEntry().getReference(), line.getMemo(), debit, credit, running));
                }
            }
        } else {
            for (JournalLine line : accountingService.allPostedLines()) {
                if (withinRange(line.getEntry().getEntryDate(), from, to)) {
                    Account account = accounts.stream()
                            .filter(a -> a.getId().equals(line.getAccountId()))
                            .findFirst()
                            .orElse(null);
                    BigDecimal debit = line.getDebitValue();
                    BigDecimal credit = line.getCreditValue();
                    totalDebits = totalDebits.add(debit);
                    totalCredits = totalCredits.add(credit);
                    String label = account == null ? line.getAccountCode() + " " + line.getAccountName()
                            : line.getAccountCode() + " " + line.getAccountName();
                    rows.add(new LedgerRow(line.getEntry().getEntryDate(), line.getEntry().getEntryNo(),
                            line.getEntry().getReference() == null ? label : label + " · " + line.getEntry().getReference(),
                            line.getMemo(), debit, credit, null));
                }
            }
        }

        model.addAttribute("accounts", accounts);
        model.addAttribute("selected", selected);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("rows", rows);
        model.addAttribute("opening", opening);
        model.addAttribute("totalDebits", totalDebits);
        model.addAttribute("totalCredits", totalCredits);
        model.addAttribute("baseCurrency", baseCurrency());
        return "accounting/ledger";
    }

    // ----- Trial balance -----

    @GetMapping("/accounting/trial-balance")
    public String trialBalance(Model model) {
        List<Account> accounts = accountingService.listAccounts();
        Map<Long, Account> byId = new LinkedHashMap<>();
        for (Account a : accounts) {
            byId.put(a.getId(), a);
        }
        Map<Long, BigDecimal> balances = accountingService.accountBalances();
        Map<Long, BigDecimal> opening = new LinkedHashMap<>();
        Map<Long, BigDecimal> debits = new LinkedHashMap<>();
        Map<Long, BigDecimal> credits = new LinkedHashMap<>();
        for (Account a : accounts) {
            opening.put(a.getId(), zero(a.getOpeningBalance()));
            debits.put(a.getId(), BigDecimal.ZERO);
            credits.put(a.getId(), BigDecimal.ZERO);
        }
        for (Object[] row : accountingService.postedTotals()) {
            Long accountId = (Long) row[0];
            BigDecimal debit = (BigDecimal) row[1];
            BigDecimal credit = (BigDecimal) row[2];
            if (debits.containsKey(accountId)) {
                debits.put(accountId, debit == null ? BigDecimal.ZERO : debit);
                credits.put(accountId, credit == null ? BigDecimal.ZERO : credit);
            }
        }

        List<TrialSection> sections = new ArrayList<>();
        BigDecimal grandOpening = BigDecimal.ZERO;
        BigDecimal grandDebits = BigDecimal.ZERO;
        BigDecimal grandCredits = BigDecimal.ZERO;
        for (AccountType accountType : AccountType.values()) {
            List<Account> members = accounts.stream()
                    .filter(a -> a.getType() == accountType)
                    .toList();
            if (members.isEmpty()) {
                continue;
            }
            List<TrialRow> rows = new ArrayList<>();
            BigDecimal sectionOpening = BigDecimal.ZERO;
            BigDecimal sectionDebits = BigDecimal.ZERO;
            BigDecimal sectionCredits = BigDecimal.ZERO;
            for (Account a : members) {
                BigDecimal ob = zero(opening.get(a.getId()));
                BigDecimal db = zero(debits.get(a.getId()));
                BigDecimal cr = zero(credits.get(a.getId()));
                BigDecimal balance = balances.getOrDefault(a.getId(), BigDecimal.ZERO);
                rows.add(new TrialRow(a.getCode(), a.getName(), ob, db, cr, balance));
                sectionOpening = sectionOpening.add(ob);
                sectionDebits = sectionDebits.add(db);
                sectionCredits = sectionCredits.add(cr);
            }
            sections.add(new TrialSection(msg("common.type." + accountType.name().toLowerCase(LocaleContextHolder.getLocale())),
                    rows, sectionOpening, sectionDebits, sectionCredits,
                    sectionOpening.add(sectionDebits).subtract(sectionCredits)));
            grandOpening = grandOpening.add(sectionOpening);
            grandDebits = grandDebits.add(sectionDebits);
            grandCredits = grandCredits.add(sectionCredits);
        }

        BigDecimal difference = grandOpening.add(grandDebits).subtract(grandCredits);
        model.addAttribute("sections", sections);
        model.addAttribute("grandOpening", grandOpening);
        model.addAttribute("grandDebits", grandDebits);
        model.addAttribute("grandCredits", grandCredits);
        model.addAttribute("difference", difference);
        model.addAttribute("asOf", LocalDate.now());
        model.addAttribute("baseCurrency", baseCurrency());
        return "accounting/trial-balance";
    }

    // ----- Periods -----

    @GetMapping("/accounting/periods")
    public String periods(Model model) {
        model.addAttribute("periods", accountingService.listPeriods());
        model.addAttribute("company", accountingService.getCompany());
        model.addAttribute("year", LocalDate.now().getYear());
        return "accounting/periods";
    }

    @PostMapping("/accounting/periods/generate")
    public String generatePeriods(@RequestParam("year") int year, RedirectAttributes redirectAttributes) {
        Company company = accountingService.getCompany();
        String fiscalYearStart = company == null ? "July" : company.getFiscalYearStart();
        int created = accountingService.generateYear(year, fiscalYearStart);
        redirectAttributes.addFlashAttribute("flashMessage",
                flash("period.generated", msg("period.generatedCount") + " " + created));
        return "redirect:/accounting/periods";
    }

    @PostMapping("/accounting/periods/{id}/toggle")
    public String togglePeriod(@PathVariable Long id) {
        accountingService.togglePeriod(id);
        return "redirect:/accounting/periods";
    }

    // ----- Fiscal year closing -----

    @GetMapping("/accounting/fiscal-close")
    public String fiscalClose(Model model) {
        Company company = accountingService.getCompany();
        String fiscalYearStart = company == null ? "July" : company.getFiscalYearStart();
        AccountingService.FiscalYearRange range = accountingService.currentFiscalYear(fiscalYearStart);
        Map<Long, BigDecimal> balances = accountingService.accountBalances();
        List<Account> accounts = accountingService.listAccounts();
        BigDecimal netIncome = BigDecimal.ZERO;
        for (Account a : accounts) {
            if (a.getType() == AccountType.REVENUE) {
                netIncome = netIncome.add(balances.getOrDefault(a.getId(), BigDecimal.ZERO));
            } else if (a.getType() == AccountType.EXPENSE) {
                netIncome = netIncome.subtract(balances.getOrDefault(a.getId(), BigDecimal.ZERO));
            }
        }
        model.addAttribute("company", company);
        model.addAttribute("range", range);
        model.addAttribute("openPeriods", accountingService.openPeriods());
        model.addAttribute("closedPeriods", accountingService.closedPeriods());
        model.addAttribute("postedEntries", accountingService.postedEntries());
        model.addAttribute("netIncome", netIncome);
        model.addAttribute("baseCurrency", baseCurrency());
        model.addAttribute("confirmText", msg("fisc.confirm"));
        return "accounting/fiscal-close";
    }

    @PostMapping("/accounting/fiscal-close")
    public String closeFiscalYear(RedirectAttributes redirectAttributes) {
        int closed = accountingService.closeFiscalYear();
        redirectAttributes.addFlashAttribute("flashMessage", flash("fisc.done", msg("fisc.closedCount") + " " + closed));
        return "redirect:/accounting/fiscal-close";
    }

    // ----- Helpers -----

    private boolean withinRange(LocalDate date, LocalDate from, LocalDate to) {
        if (date == null) {
            return false;
        }
        if (from != null && date.isBefore(from)) {
            return false;
        }
        return to == null || !date.isAfter(to);
    }

    private static BigDecimal netAmount(AccountType type, BigDecimal debit, BigDecimal credit) {
        BigDecimal net = debit.subtract(credit);
        return (type == AccountType.ASSET || type == AccountType.EXPENSE) ? net : net.negate();
    }

    private String baseCurrency() {
        Company company = accountingService.getCompany();
        if (company != null && company.getCurrencyCode() != null) {
            return company.getCurrencyCode();
        }
        return "RWF";
    }

    private Map<String, String> typeLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        for (AccountType type : AccountType.values()) {
            labels.put(type.name(), msg("common.type." + type.name().toLowerCase(LocaleContextHolder.getLocale())));
        }
        return labels;
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record TypeSection(String typeLabel, List<Account> accounts, BigDecimal total) {}

    private record TrialSection(String typeLabel, List<TrialRow> rows, BigDecimal opening,
                                BigDecimal debits, BigDecimal credits, BigDecimal balance) {}

    private record TrialRow(String code, String name, BigDecimal opening, BigDecimal debits,
                            BigDecimal credits, BigDecimal balance) {}

    public record LedgerRow(LocalDate date, String entryNo, String reference, String memo,
                            BigDecimal debit, BigDecimal credit, BigDecimal running) {}
}