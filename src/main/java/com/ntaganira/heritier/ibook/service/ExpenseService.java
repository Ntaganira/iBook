/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : ExpenseService.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Direct expenses with double-entry ledger posting
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.ExpenseForm;
import com.ntaganira.heritier.ibook.entity.*;
import com.ntaganira.heritier.ibook.enums.ExpenseStatus;
import com.ntaganira.heritier.ibook.enums.JournalEntryStatus;
import com.ntaganira.heritier.ibook.enums.JournalEntryType;
import com.ntaganira.heritier.ibook.enums.PaymentMethod;
import com.ntaganira.heritier.ibook.enums.TaxTreatment;
import com.ntaganira.heritier.ibook.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ExpenseService {

    private static final String MODULE = "expenses";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String VAT_INPUT_ACCOUNT_CODE = "1402";
    private static final String VAT_FALLBACK_ACCOUNT_CODE = "2101";
    private static final String DEFAULT_EXPENSE_CODE = "5000";

    private final ExpenseRepository expenseRepository;
    private final VendorRepository vendorRepository;
    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final NumberingSequenceRepository numberingSequenceRepository;
    private final CompanyRepository companyRepository;
    private final TaxRateRepository taxRateRepository;
    private final AuditService auditService;

    public ExpenseService(ExpenseRepository expenseRepository,
                          VendorRepository vendorRepository,
                          AccountRepository accountRepository,
                          JournalEntryRepository journalEntryRepository,
                          NumberingSequenceRepository numberingSequenceRepository,
                          CompanyRepository companyRepository,
                          TaxRateRepository taxRateRepository,
                          AuditService auditService) {
        this.expenseRepository = expenseRepository;
        this.vendorRepository = vendorRepository;
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.numberingSequenceRepository = numberingSequenceRepository;
        this.companyRepository = companyRepository;
        this.taxRateRepository = taxRateRepository;
        this.auditService = auditService;
    }

    // ----- Queries -----

    @Transactional(readOnly = true)
    public Page<Expense> list(String q, String status, Long vendorId,
                              LocalDate from, LocalDate to, Pageable pageable) {
        return expenseRepository.search(trimToNull(q), parseStatus(status), vendorId, from, to, pageable);
    }

    @Transactional(readOnly = true)
    public Expense get(Long id) {
        return id == null ? null : expenseRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public JournalEntry journalFor(Expense expense) {
        if (expense == null || expense.getJournalEntryId() == null) {
            return null;
        }
        return journalEntryRepository.findById(expense.getJournalEntryId()).orElse(null);
    }

    @Transactional(readOnly = true)
    public String baseCurrency() {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElse(null);
        return company == null || company.getCurrencyCode() == null ? "RWF" : company.getCurrencyCode();
    }

    @Transactional(readOnly = true)
    public ExpenseSummary summary() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
        LocalDate yearStart = today.withDayOfYear(1);
        return new ExpenseSummary(
                expenseRepository.count(),
                expenseRepository.countByStatus(ExpenseStatus.DRAFT),
                expenseRepository.countByStatus(ExpenseStatus.POSTED),
                expenseRepository.countByStatus(ExpenseStatus.VOID),
                zero(expenseRepository.totalPosted()),
                zero(expenseRepository.totalBetween(monthStart, monthEnd)),
                zero(expenseRepository.totalBetween(yearStart, today)),
                zero(expenseRepository.taxBetween(monthStart, monthEnd)));
    }

    /** Posted spend grouped by expense account, for the breakdown panel. */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> spendByAccount(LocalDate from, LocalDate to) {
        Map<String, BigDecimal> byAccount = new LinkedHashMap<>();
        for (Expense expense : expenseRepository.findAll()) {
            if (expense.getStatus() != ExpenseStatus.POSTED) {
                continue;
            }
            LocalDate date = expense.getExpenseDate();
            if (date == null || (from != null && date.isBefore(from)) || (to != null && date.isAfter(to))) {
                continue;
            }
            for (ExpenseLine line : expense.getLines()) {
                String key = line.getExpenseAccountCode() == null
                        ? "—"
                        : line.getExpenseAccountCode() + " - " + line.getExpenseAccountName();
                byAccount.merge(key, zero(line.getAmount()), BigDecimal::add);
            }
        }
        return byAccount;
    }

    // ----- Create / update -----

    @Transactional
    public Expense save(ExpenseForm form, Long id, String username) {
        Expense expense;
        if (id == null) {
            expense = new Expense();
            expense.setExpenseNo(nextExpenseNo());
            expense.setCreatedBy(username);
            expense.setStatus(ExpenseStatus.DRAFT);
        } else {
            expense = expenseRepository.findById(id).orElseThrow();
            if (!expense.isEditable()) {
                throw new IllegalStateException("Only draft expenses can be edited");
            }
            expense.getLines().clear();
        }

        Vendor vendor = form.getVendorId() == null ? null
                : vendorRepository.findById(form.getVendorId()).orElse(null);
        expense.setVendorId(vendor == null ? null : vendor.getId());
        String payee = vendor != null ? vendor.getName() : trimToNull(form.getPayeeName());
        if (payee == null) {
            throw new IllegalStateException("An expense needs a payee");
        }
        expense.setPayeeName(payee);

        Account payment = accountRepository.findById(form.getPaymentAccountId()).orElseThrow();
        expense.setPaymentAccountId(payment.getId());
        expense.setPaymentAccountCode(payment.getCode());
        expense.setPaymentAccountName(payment.getName());
        expense.setPaymentMethod(parseMethod(form.getPaymentMethod()));
        expense.setExpenseDate(form.getExpenseDate() == null ? LocalDate.now() : form.getExpenseDate());
        expense.setReference(trimToNull(form.getReference()));
        expense.setCurrencyCode(form.getCurrencyCode() == null || form.getCurrencyCode().isBlank()
                ? baseCurrency() : form.getCurrencyCode());
        expense.setMemo(trimToNull(form.getMemo()));
        expense.setNotes(trimToNull(form.getNotes()));

        Account fallbackExpense = accountRepository.findByCodeIgnoreCase(DEFAULT_EXPENSE_CODE).orElse(null);
        int sort = 0;
        for (ExpenseForm.Line lineForm : form.filledLines()) {
            Account account = lineForm.getExpenseAccountId() == null
                    ? fallbackExpense
                    : accountRepository.findById(lineForm.getExpenseAccountId()).orElse(fallbackExpense);
            ResolvedTax resolved = resolveTax(lineForm.getTaxRateId(), lineForm.taxRateValue());
            BigDecimal amount = lineForm.amountValue();
            BigDecimal tax = taxOf(amount, resolved.rate());
            expense.addLine(ExpenseLine.builder()
                    .description(lineForm.getDescription().trim())
                    .amount(amount)
                    .taxRate(resolved.rate())
                    .taxRateId(resolved.rateId())
                    .taxTreatment(resolved.treatment())
                    .lineTax(tax)
                    .lineTotal(amount.add(tax))
                    .expenseAccountId(account == null ? null : account.getId())
                    .expenseAccountCode(account == null ? null : account.getCode())
                    .expenseAccountName(account == null ? null : account.getName())
                    .sortOrder(sort++)
                    .build());
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        for (ExpenseLine l : expense.getLines()) {
            subtotal = subtotal.add(zero(l.getAmount()));
            taxTotal = taxTotal.add(zero(l.getLineTax()));
        }
        expense.setSubtotal(subtotal);
        expense.setTaxAmount(taxTotal);
        expense.setTotal(subtotal.add(taxTotal));

        Expense saved = expenseRepository.save(expense);
        auditService.log(MODULE, id == null ? "CREATE_EXPENSE" : "UPDATE_EXPENSE",
                "expense#" + saved.getId(), saved.getExpenseNo() + " — " + saved.getPayeeName());

        if (form.isPostNow()) {
            saved = post(saved.getId());
        }
        return saved;
    }

    // ----- Posting to the general ledger -----

    /**
     * An expense is spend that has already been paid, so it posts in one entry: the expense
     * accounts and input VAT are debited and the cash or bank account is credited directly.
     * Accounts payable is never involved — that is what a vendor bill is for.
     */
    @Transactional
    public Expense post(Long id) {
        Expense expense = expenseRepository.findById(id).orElseThrow();
        if (expense.isPosted()) {
            return expense;
        }
        if (expense.getStatus() == ExpenseStatus.VOID) {
            throw new IllegalStateException("A void expense cannot be posted");
        }
        if (expense.getLines().isEmpty()) {
            throw new IllegalStateException("An expense with no line items cannot be posted");
        }

        Account payment = accountRepository.findById(expense.getPaymentAccountId())
                .orElseThrow(() -> new IllegalStateException("The payment account no longer exists"));
        Account vatInput = accountRepository.findByCodeIgnoreCase(VAT_INPUT_ACCOUNT_CODE)
                .or(() -> accountRepository.findByCodeIgnoreCase(VAT_FALLBACK_ACCOUNT_CODE))
                .orElse(null);

        JournalEntry entry = JournalEntry.builder()
                .entryNo(nextJournalNo())
                .entryDate(expense.getExpenseDate())
                .type(JournalEntryType.PAYMENT)
                .status(JournalEntryStatus.POSTED)
                .reference(expense.getExpenseNo())
                .memo("Expense " + expense.getExpenseNo() + " — " + expense.getPayeeName())
                .createdBy(AuditService.currentUsername())
                .build();

        Map<Long, BigDecimal> spendByAccount = new LinkedHashMap<>();
        Map<Long, Account> accountCache = new LinkedHashMap<>();
        for (ExpenseLine line : expense.getLines()) {
            if (line.getExpenseAccountId() == null) {
                continue;
            }
            spendByAccount.merge(line.getExpenseAccountId(), zero(line.getAmount()), BigDecimal::add);
            accountCache.computeIfAbsent(line.getExpenseAccountId(),
                    key -> accountRepository.findById(key).orElse(null));
        }

        int order = 0;
        BigDecimal debitedSpend = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> spend : spendByAccount.entrySet()) {
            Account account = accountCache.get(spend.getKey());
            if (account == null || spend.getValue().signum() == 0) {
                continue;
            }
            debitedSpend = debitedSpend.add(spend.getValue());
            entry.addLine(JournalLine.builder()
                    .accountId(account.getId())
                    .accountCode(account.getCode())
                    .accountName(account.getName())
                    .memo(expense.getExpenseNo())
                    .debit(spend.getValue())
                    .credit(BigDecimal.ZERO)
                    .sortOrder(order++)
                    .build());
        }

        BigDecimal tax = zero(expense.getTaxAmount());
        if (tax.signum() != 0 && vatInput != null) {
            entry.addLine(JournalLine.builder()
                    .accountId(vatInput.getId())
                    .accountCode(vatInput.getCode())
                    .accountName(vatInput.getName())
                    .memo("Input VAT on " + expense.getExpenseNo())
                    .debit(tax)
                    .credit(BigDecimal.ZERO)
                    .sortOrder(order++)
                    .build());
        }

        // Rounding drift lands on the last debit line so the entry balances exactly.
        BigDecimal rounding = zero(expense.getTotal()).subtract(debitedSpend).subtract(tax);
        if (rounding.signum() != 0 && !entry.getLines().isEmpty()) {
            JournalLine last = entry.getLines().get(entry.getLines().size() - 1);
            last.setDebit(last.getDebitValue().add(rounding));
        }

        entry.addLine(JournalLine.builder()
                .accountId(payment.getId())
                .accountCode(payment.getCode())
                .accountName(payment.getName())
                .memo(expense.getPayeeName())
                .debit(BigDecimal.ZERO)
                .credit(zero(expense.getTotal()))
                .sortOrder(order)
                .build());

        entry.setTotalDebits(sumDebits(entry));
        entry.setTotalCredits(sumCredits(entry));
        JournalEntry savedEntry = journalEntryRepository.save(entry);

        expense.setJournalEntryId(savedEntry.getId());
        expense.setStatus(ExpenseStatus.POSTED);
        Expense saved = expenseRepository.save(expense);

        auditService.log(MODULE, "POST_EXPENSE", "expense#" + saved.getId(),
                saved.getExpenseNo() + " posted as " + savedEntry.getEntryNo());
        return saved;
    }

    // ----- Lifecycle -----

    @Transactional
    public Expense voidExpense(Long id, String reason) {
        Expense expense = expenseRepository.findById(id).orElseThrow();
        if (expense.getStatus() == ExpenseStatus.VOID) {
            return expense;
        }
        if (expense.isPosted()) {
            JournalEntry original = journalEntryRepository.findById(expense.getJournalEntryId()).orElse(null);
            if (original != null) {
                JournalEntry reversal = JournalEntry.builder()
                        .entryNo(nextJournalNo())
                        .entryDate(LocalDate.now())
                        .type(JournalEntryType.ADJUSTMENT)
                        .status(JournalEntryStatus.POSTED)
                        .reference(expense.getExpenseNo())
                        .memo("Reversal of " + original.getEntryNo() + " — voided expense "
                                + expense.getExpenseNo())
                        .createdBy(AuditService.currentUsername())
                        .build();
                int order = 0;
                for (JournalLine line : original.getLines()) {
                    reversal.addLine(JournalLine.builder()
                            .accountId(line.getAccountId())
                            .accountCode(line.getAccountCode())
                            .accountName(line.getAccountName())
                            .memo(line.getMemo())
                            .debit(line.getCreditValue())
                            .credit(line.getDebitValue())
                            .sortOrder(order++)
                            .build());
                }
                reversal.setTotalDebits(sumDebits(reversal));
                reversal.setTotalCredits(sumCredits(reversal));
                journalEntryRepository.save(reversal);
            }
        }
        expense.setStatus(ExpenseStatus.VOID);
        Expense saved = expenseRepository.save(expense);
        auditService.log(MODULE, "VOID_EXPENSE", "expense#" + id,
                saved.getExpenseNo() + (reason == null || reason.isBlank() ? "" : " — " + reason.trim()));
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Expense expense = expenseRepository.findById(id).orElse(null);
        if (expense == null) {
            return;
        }
        if (!expense.isEditable()) {
            throw new IllegalStateException("Only draft expenses can be deleted");
        }
        expenseRepository.delete(expense);
        auditService.log(MODULE, "DELETE_EXPENSE", "expense#" + id, expense.getExpenseNo() + " draft deleted");
    }

    // ----- Helpers -----

    @Transactional(readOnly = true)
    public String previewNextNumber() {
        NumberingSequence seq = numberingSequenceRepository.findByDocType("EXPENSE").orElse(null);
        return seq == null ? "EXP-0001" : seq.previewNext();
    }

    private String nextExpenseNo() {
        NumberingSequence seq = numberingSequenceRepository.findByDocType("EXPENSE").orElse(null);
        if (seq != null) {
            long next = seq.getNextNumber();
            String prefix = seq.getPrefix() == null ? "EXP-" : seq.getPrefix();
            String suffix = seq.getSuffix() == null ? "" : seq.getSuffix();
            int padding = seq.getPadding() == 0 ? 4 : seq.getPadding();
            String number = prefix + String.format("%0" + padding + "d", next) + suffix;
            seq.setNextNumber(next + 1);
            numberingSequenceRepository.save(seq);
            return number;
        }
        return "EXP-" + LocalDate.now().getYear() + "-" + String.format("%05d", RANDOM.nextInt(100000));
    }

    private String nextJournalNo() {
        NumberingSequence seq = numberingSequenceRepository.findByDocType("JOURNAL").orElse(null);
        if (seq != null) {
            long next = seq.getNextNumber();
            String prefix = seq.getPrefix() == null ? "JE-" : seq.getPrefix();
            int padding = seq.getPadding() == 0 ? 5 : seq.getPadding();
            String number = prefix + String.format("%0" + padding + "d", next);
            seq.setNextNumber(next + 1);
            numberingSequenceRepository.save(seq);
            return number;
        }
        return "JE-" + LocalDate.now().getYear() + "-" + String.format("%05d", RANDOM.nextInt(100000));
    }

    private ResolvedTax resolveTax(Long taxRateId, BigDecimal fallbackRate) {
        if (taxRateId != null) {
            TaxRate configured = taxRateRepository.findById(taxRateId).orElse(null);
            if (configured != null) {
                return new ResolvedTax(configured.getId(), zero(configured.getRate()), configured.getTreatment());
            }
        }
        BigDecimal rate = zero(fallbackRate);
        return new ResolvedTax(null, rate,
                rate.signum() > 0 ? TaxTreatment.STANDARD : TaxTreatment.ZERO_RATED);
    }

    private record ResolvedTax(Long rateId, BigDecimal rate, TaxTreatment treatment) {}

    private static BigDecimal taxOf(BigDecimal base, BigDecimal rate) {
        return zero(base).multiply(zero(rate)).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal sumDebits(JournalEntry entry) {
        BigDecimal total = BigDecimal.ZERO;
        for (JournalLine line : entry.getLines()) {
            total = total.add(line.getDebitValue());
        }
        return total;
    }

    private static BigDecimal sumCredits(JournalEntry entry) {
        BigDecimal total = BigDecimal.ZERO;
        for (JournalLine line : entry.getLines()) {
            total = total.add(line.getCreditValue());
        }
        return total;
    }

    private static ExpenseStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ExpenseStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static PaymentMethod parseMethod(String method) {
        if (method == null || method.isBlank()) {
            return PaymentMethod.CASH;
        }
        try {
            return PaymentMethod.valueOf(method.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return PaymentMethod.CASH;
        }
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record ExpenseSummary(long all, long draft, long posted, long voided,
                                 BigDecimal totalPosted, BigDecimal thisMonth,
                                 BigDecimal thisYear, BigDecimal taxThisMonth) {}
}
