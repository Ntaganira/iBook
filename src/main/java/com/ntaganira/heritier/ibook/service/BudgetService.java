/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : BudgetService.java
 * - Date      : 2026. 09. 20.
 * - User      : Hntaganira
 * - Desc      : Operating budgets and what the ledger actually did against them
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.BudgetForm;
import com.ntaganira.heritier.ibook.entity.Account;
import com.ntaganira.heritier.ibook.entity.Budget;
import com.ntaganira.heritier.ibook.entity.BudgetLine;
import com.ntaganira.heritier.ibook.entity.Company;
import com.ntaganira.heritier.ibook.enums.AccountType;
import com.ntaganira.heritier.ibook.enums.BudgetStatus;
import com.ntaganira.heritier.ibook.repository.AccountRepository;
import com.ntaganira.heritier.ibook.repository.BudgetRepository;
import com.ntaganira.heritier.ibook.repository.CompanyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class BudgetService {

    private static final String MODULE = "budgets";
    private static final int MONTHS = 12;

    private final BudgetRepository budgetRepository;
    private final AccountRepository accountRepository;
    private final CompanyRepository companyRepository;
    private final ReportService reportService;
    private final AuditService auditService;

    public BudgetService(BudgetRepository budgetRepository,
                         AccountRepository accountRepository,
                         CompanyRepository companyRepository,
                         ReportService reportService,
                         AuditService auditService) {
        this.budgetRepository = budgetRepository;
        this.accountRepository = accountRepository;
        this.companyRepository = companyRepository;
        this.reportService = reportService;
        this.auditService = auditService;
    }

    // ----- Queries -----

    @Transactional(readOnly = true)
    public Page<Budget> list(String q, String status, Integer fiscalYear, Pageable pageable) {
        return budgetRepository.search(trimToNull(q), parseStatus(status), fiscalYear, pageable);
    }

    @Transactional(readOnly = true)
    public Budget get(Long id) {
        if (id == null) {
            return null;
        }
        Budget budget = budgetRepository.findById(id).orElse(null);
        if (budget != null) {
            budget.getLines().size();
        }
        return budget;
    }

    @Transactional(readOnly = true)
    public List<Budget> all() {
        return budgetRepository.findAllByOrderByFiscalYearDescNameAsc();
    }

    @Transactional(readOnly = true)
    public List<Integer> fiscalYears() {
        return budgetRepository.distinctFiscalYears();
    }

    @Transactional(readOnly = true)
    public String baseCurrency() {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElse(null);
        return company == null || company.getCurrencyCode() == null ? "RWF" : company.getCurrencyCode();
    }

    @Transactional(readOnly = true)
    public boolean nameExists(String name, Long id) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return id == null
                ? budgetRepository.existsByNameIgnoreCase(name.trim())
                : budgetRepository.existsByNameIgnoreCaseAndIdNot(name.trim(), id);
    }

    /**
     * A budget covers trading, so only revenue and expense accounts can be budgeted here. Buying an
     * asset is a capital budget, which is a different document and deliberately out of scope.
     */
    @Transactional(readOnly = true)
    public List<Account> budgetableAccounts() {
        List<Account> rows = new ArrayList<>();
        for (Account account : accountRepository.findAllByOrderByCodeAsc()) {
            if (account.isActive()
                    && (account.getType() == AccountType.REVENUE
                        || account.getType() == AccountType.EXPENSE)) {
                rows.add(account);
            }
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public BudgetSummary summary() {
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        for (Budget budget : budgetRepository.approved()) {
            revenue = revenue.add(zero(budget.getTotalRevenue()));
            expense = expense.add(zero(budget.getTotalExpense()));
        }
        return new BudgetSummary(
                budgetRepository.count(),
                budgetRepository.countByStatus(BudgetStatus.DRAFT),
                budgetRepository.countByStatus(BudgetStatus.APPROVED),
                budgetRepository.countByStatus(BudgetStatus.CLOSED),
                revenue, expense, revenue.subtract(expense));
    }

    // ----- Create / update -----

    @Transactional
    public Budget save(BudgetForm form, Long id, String username) {
        Budget budget;
        if (id == null) {
            budget = new Budget();
            budget.setCreatedBy(username);
            budget.setStatus(BudgetStatus.DRAFT);
        } else {
            budget = budgetRepository.findById(id).orElseThrow();
            if (!budget.isEditable()) {
                throw new IllegalStateException("An approved budget has to be reopened before it can be edited");
            }
        }

        if (trimToNull(form.getName()) == null) {
            throw new IllegalArgumentException("A budget needs a name");
        }
        LocalDate start = form.getStartDate() == null
                ? LocalDate.now().withDayOfYear(1) : form.getStartDate().withDayOfMonth(1);

        budget.setName(form.getName().trim());
        budget.setFiscalYear(form.fiscalYearValue());
        budget.setStartDate(start);
        budget.setDescription(trimToNull(form.getDescription()));
        budget.setNotes(trimToNull(form.getNotes()));

        applyLines(budget, form);

        Budget saved = budgetRepository.save(budget);
        auditService.log(MODULE, id == null ? "CREATE_BUDGET" : "UPDATE_BUDGET",
                "budget#" + saved.getId(), saved.getName());

        if (form.isApproveNow() && saved.isDraft()) {
            saved = approve(saved.getId(), username);
        }
        return saved;
    }

    /**
     * A line can be given month by month, or as one annual figure spread evenly across the twelve.
     * Filled-in months win, because they are the more specific statement. An even spread puts the
     * rounding drift on the last month so the twelve add back to the annual figure exactly.
     */
    private void applyLines(Budget budget, BudgetForm form) {
        budget.getLines().clear();
        Set<Long> seen = new HashSet<>();
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        int sort = 0;

        for (BudgetForm.Line row : form.getLines()) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            Account account = accountRepository.findById(row.getAccountId()).orElse(null);
            if (account == null) {
                continue;
            }
            if (account.getType() != AccountType.REVENUE && account.getType() != AccountType.EXPENSE) {
                throw new IllegalArgumentException(account.getCode() + " " + account.getName()
                        + " is not a revenue or expense account");
            }
            if (!seen.add(account.getId())) {
                throw new IllegalArgumentException(account.getCode() + " " + account.getName()
                        + " is budgeted twice");
            }

            BudgetLine line = new BudgetLine();
            line.setAccountId(account.getId());
            line.setAccountCode(account.getCode());
            line.setAccountName(account.getName());
            line.setAccountType(account.getType());
            line.setNotes(trimToNull(row.getNotes()));
            line.setSortOrder(sort++);

            if (row.hasMonthlyDetail()) {
                for (int m = 1; m <= MONTHS; m++) {
                    line.setAmountForMonth(m, zero(row.monthAt(m)));
                }
            } else {
                spreadEvenly(line, zero(row.getAnnualAmount()));
            }
            BigDecimal annual = line.getMonthlyTotal();
            if (annual.signum() < 0) {
                throw new IllegalArgumentException(account.getCode() + " " + account.getName()
                        + " is budgeted at a negative figure");
            }
            line.setAnnualAmount(annual);

            if (account.getType() == AccountType.REVENUE) {
                revenue = revenue.add(annual);
            } else {
                expense = expense.add(annual);
            }
            budget.addLine(line);
        }

        if (budget.getLines().isEmpty()) {
            throw new IllegalArgumentException("A budget needs at least one account on it");
        }
        budget.setTotalRevenue(revenue);
        budget.setTotalExpense(expense);
    }

    private static void spreadEvenly(BudgetLine line, BigDecimal annual) {
        if (annual.signum() == 0) {
            for (int m = 1; m <= MONTHS; m++) {
                line.setAmountForMonth(m, BigDecimal.ZERO);
            }
            return;
        }
        BigDecimal each = annual.divide(new BigDecimal(MONTHS), 2, RoundingMode.HALF_UP);
        BigDecimal running = BigDecimal.ZERO;
        for (int m = 1; m < MONTHS; m++) {
            line.setAmountForMonth(m, each);
            running = running.add(each);
        }
        line.setAmountForMonth(MONTHS, annual.subtract(running));
    }

    // ----- Lifecycle -----

    @Transactional
    public Budget approve(Long id, String username) {
        Budget budget = budgetRepository.findById(id).orElseThrow();
        if (!budget.isDraft()) {
            throw new IllegalStateException("Only a draft budget can be approved");
        }
        budget.setStatus(BudgetStatus.APPROVED);
        budget.setApprovedAt(LocalDateTime.now());
        budget.setApprovedBy(username);
        Budget saved = budgetRepository.save(budget);
        auditService.log(MODULE, "APPROVE_BUDGET", "budget#" + id, saved.getName());
        return saved;
    }

    @Transactional
    public Budget reopen(Long id) {
        Budget budget = budgetRepository.findById(id).orElseThrow();
        if (budget.isClosed()) {
            throw new IllegalStateException("A closed budget cannot be reopened");
        }
        if (budget.isDraft()) {
            throw new IllegalStateException("This budget is already a draft");
        }
        budget.setStatus(BudgetStatus.DRAFT);
        budget.setApprovedAt(null);
        budget.setApprovedBy(null);
        Budget saved = budgetRepository.save(budget);
        auditService.log(MODULE, "REOPEN_BUDGET", "budget#" + id, saved.getName());
        return saved;
    }

    @Transactional
    public Budget close(Long id) {
        Budget budget = budgetRepository.findById(id).orElseThrow();
        if (!budget.isApproved()) {
            throw new IllegalStateException("Only an approved budget can be closed");
        }
        budget.setStatus(BudgetStatus.CLOSED);
        Budget saved = budgetRepository.save(budget);
        auditService.log(MODULE, "CLOSE_BUDGET", "budget#" + id, saved.getName());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Budget budget = budgetRepository.findById(id).orElse(null);
        if (budget == null) {
            return;
        }
        if (!budget.isDraft()) {
            throw new IllegalStateException("Only a draft budget can be deleted");
        }
        budgetRepository.delete(budget);
        auditService.log(MODULE, "DELETE_BUDGET", "budget#" + id, budget.getName());
    }

    // ----- Budget against actual -----

    /**
     * Compares a slice of a budget with what the ledger actually did over the same months. The
     * comparison runs on whole months because that is the grain the budget is held at — a part month
     * would have to invent a split of a figure nobody entered that way.
     *
     * <p>Variance is reported as <em>favourable or adverse</em>, not as raw arithmetic: earning more
     * revenue than planned and spending less than planned are both good, and they have opposite
     * signs. Showing "actual minus budget" for both would put a windfall and an overspend in the
     * same column with the same colour.
     */
    @Transactional(readOnly = true)
    public Comparison compare(Budget budget, int fromMonth, int toMonth) {
        if (budget == null) {
            return null;
        }
        int from = Math.max(1, Math.min(MONTHS, fromMonth));
        int to = Math.max(from, Math.min(MONTHS, toMonth));
        LocalDate start = budget.getStartDate().plusMonths(from - 1L);
        LocalDate end = budget.getStartDate().plusMonths(to).minusDays(1);

        Map<Long, BigDecimal> actuals = reportService.signedMovementsBetween(start, end);

        List<ComparisonRow> revenue = new ArrayList<>();
        List<ComparisonRow> expense = new ArrayList<>();
        BigDecimal budgetRevenue = BigDecimal.ZERO;
        BigDecimal actualRevenue = BigDecimal.ZERO;
        BigDecimal budgetExpense = BigDecimal.ZERO;
        BigDecimal actualExpense = BigDecimal.ZERO;

        for (BudgetLine line : budget.getLines()) {
            BigDecimal budgeted = line.amountForMonths(from, to);
            BigDecimal actual = zero(actuals.get(line.getAccountId()));
            boolean revenueLine = line.getAccountType() == AccountType.REVENUE;
            ComparisonRow row = row(line.getAccountCode(), line.getAccountName(),
                    line.getAccountId(), budgeted, actual, revenueLine);
            if (revenueLine) {
                revenue.add(row);
                budgetRevenue = budgetRevenue.add(budgeted);
                actualRevenue = actualRevenue.add(actual);
            } else {
                expense.add(row);
                budgetExpense = budgetExpense.add(budgeted);
                actualExpense = actualExpense.add(actual);
            }
        }

        ComparisonRow revenueTotal = row(null, null, null, budgetRevenue, actualRevenue, true);
        ComparisonRow expenseTotal = row(null, null, null, budgetExpense, actualExpense, false);
        ComparisonRow profitTotal = row(null, null, null,
                budgetRevenue.subtract(budgetExpense),
                actualRevenue.subtract(actualExpense), true);

        return new Comparison(budget, from, to, start, end, revenue, expense,
                revenueTotal, expenseTotal, profitTotal, unbudgetedActivity(budget, actuals));
    }

    /**
     * Revenue and expense the ledger saw on accounts the budget never named. Left out of the
     * variance rather than folded in silently, because a budget cannot be over or under on
     * something it does not mention — but the figure has to be visible or the comparison quietly
     * misses real trading.
     */
    private List<ComparisonRow> unbudgetedActivity(Budget budget, Map<Long, BigDecimal> actuals) {
        Set<Long> budgeted = new HashSet<>();
        for (BudgetLine line : budget.getLines()) {
            budgeted.add(line.getAccountId());
        }
        List<ComparisonRow> rows = new ArrayList<>();
        for (Account account : accountRepository.findAllByOrderByCodeAsc()) {
            if (budgeted.contains(account.getId())) {
                continue;
            }
            if (account.getType() != AccountType.REVENUE && account.getType() != AccountType.EXPENSE) {
                continue;
            }
            BigDecimal actual = zero(actuals.get(account.getId()));
            if (actual.signum() == 0) {
                continue;
            }
            rows.add(row(account.getCode(), account.getName(), account.getId(),
                    BigDecimal.ZERO, actual, account.getType() == AccountType.REVENUE));
        }
        return rows;
    }

    private static ComparisonRow row(String code, String name, Long accountId,
                                     BigDecimal budgeted, BigDecimal actual, boolean revenueLine) {
        BigDecimal variance = revenueLine
                ? actual.subtract(budgeted)
                : budgeted.subtract(actual);
        BigDecimal percent = budgeted.signum() == 0
                ? BigDecimal.ZERO
                : variance.multiply(new BigDecimal("100"))
                        .divide(budgeted.abs(), 1, RoundingMode.HALF_UP);
        return new ComparisonRow(code, name, accountId, budgeted, actual, variance, percent,
                revenueLine);
    }

    // ----- Helpers -----

    private static BudgetStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return BudgetStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
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

    public record ComparisonRow(String accountCode, String accountName, Long accountId,
                                BigDecimal budgeted, BigDecimal actual, BigDecimal variance,
                                BigDecimal variancePercent, boolean revenueLine) {
        public boolean isFavourable() {
            return variance.signum() > 0;
        }

        public boolean isAdverse() {
            return variance.signum() < 0;
        }

        public BigDecimal varianceMagnitude() {
            return variance.abs();
        }
    }

    public record Comparison(Budget budget, int fromMonth, int toMonth,
                             LocalDate from, LocalDate to,
                             List<ComparisonRow> revenue, List<ComparisonRow> expense,
                             ComparisonRow revenueTotal, ComparisonRow expenseTotal,
                             ComparisonRow profitTotal, List<ComparisonRow> unbudgeted) {
        public boolean hasUnbudgeted() {
            return !unbudgeted.isEmpty();
        }

        public BigDecimal unbudgetedTotal() {
            BigDecimal total = BigDecimal.ZERO;
            for (ComparisonRow row : unbudgeted) {
                total = total.add(row.revenueLine() ? row.actual() : row.actual().negate());
            }
            return total;
        }
    }

    public record BudgetSummary(long all, long draft, long approved, long closed,
                                BigDecimal approvedRevenue, BigDecimal approvedExpense,
                                BigDecimal approvedProfit) {}
}
