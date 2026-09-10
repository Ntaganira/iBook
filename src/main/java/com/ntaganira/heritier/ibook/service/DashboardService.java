/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : DashboardService.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : Dashboard KPIs, chart series, recent activity and alerts
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntaganira.heritier.ibook.entity.Account;
import com.ntaganira.heritier.ibook.entity.Company;
import com.ntaganira.heritier.ibook.entity.JournalEntry;
import com.ntaganira.heritier.ibook.entity.JournalLine;
import com.ntaganira.heritier.ibook.entity.User;
import com.ntaganira.heritier.ibook.enums.AccountType;
import com.ntaganira.heritier.ibook.enums.JournalEntryStatus;
import com.ntaganira.heritier.ibook.repository.AccountingPeriodRepository;
import com.ntaganira.heritier.ibook.repository.JournalEntryRepository;
import com.ntaganira.heritier.ibook.repository.UserRepository;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final int ACTIVITY_LIMIT = 6;
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final AccountingService accounting;
    private final JournalEntryRepository journalEntryRepository;
    private final AccountingPeriodRepository periodRepository;
    private final UserRepository userRepository;
    private final MessageSource messageSource;
    private final ObjectMapper objectMapper;

    public DashboardService(AccountingService accounting,
                            JournalEntryRepository journalEntryRepository,
                            AccountingPeriodRepository periodRepository,
                            UserRepository userRepository,
                            MessageSource messageSource,
                            ObjectMapper objectMapper) {
        this.accounting = accounting;
        this.journalEntryRepository = journalEntryRepository;
        this.periodRepository = periodRepository;
        this.userRepository = userRepository;
        this.messageSource = messageSource;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Snapshot snapshot(String username) {
        LocalDate today = LocalDate.now();
        Company company = accounting.getCompany();
        String baseCurrency = currency(company);

        List<Account> accounts = accounting.listAccounts();
        Map<Long, Account> byId = accounts.stream().collect(Collectors.toMap(Account::getId, a -> a));
        Map<Long, BigDecimal> balances = accounting.accountBalances();
        Set<Long> cashIds = cashAccountIds(accounts);

        BigDecimal revenueTotal = totals(accounts, balances, AccountType.REVENUE);
        BigDecimal expenseTotal = totals(accounts, balances, AccountType.EXPENSE).abs();
        BigDecimal profitTotal = revenueTotal.subtract(expenseTotal);
        BigDecimal receivableTotal = balanceOfCode(accounts, balances, "1201");
        BigDecimal payableTotal = balanceOfCode(accounts, balances, "2001");
        BigDecimal inventoryTotal = balanceOfCode(accounts, balances, "1301");
        BigDecimal cashTotal = sums(balances, cashIds);
        BigDecimal cashOpening = cashOpening(accounts, cashIds);
        BigDecimal margin = revenueTotal.signum() == 0
                ? ZERO
                : profitTotal.multiply(BigDecimal.valueOf(100)).divide(revenueTotal, 1, RoundingMode.HALF_UP);

        List<YearMonth> months = fiscalYearMonths(company);
        Map<YearMonth, Monthly> monthly = initMonthly(months);
        aggregate(accounting.allPostedLines(), byId, cashIds, monthly);

        YearMonth thisYm = YearMonth.from(today);
        YearMonth lastYm = thisYm.minusMonths(1);
        Monthly t = monthly.getOrDefault(thisYm, Monthly.empty());
        Monthly p = monthly.getOrDefault(lastYm, Monthly.empty());

        List<ActivityRow> activity = activity(byId, cashIds);
        List<AlertItem> alerts = alerts(accounts, balances, revenueTotal, t.revenue, cashTotal, today);

        List<String> labels = months.stream()
                .map(ym -> ym.getMonth().getDisplayName(TextStyle.SHORT, LocaleContextHolder.getLocale()))
                .toList();
        List<Double> revenueSeries = months.stream().map(m -> millions(monthly.get(m).revenue)).toList();
        List<Double> expenseSeries = months.stream().map(m -> millions(monthly.get(m).expense)).toList();
        List<Double> cashIn = months.stream().map(m -> millions(monthly.get(m).cashIn)).toList();
        List<Double> cashOut = months.stream().map(m -> millions(monthly.get(m).cashOut)).toList();
        List<Double> profitSeries = months.stream()
                .map(m -> millions(monthly.get(m).revenue.subtract(monthly.get(m).expense)))
                .toList();

        List<Account> expenseAccounts = accounts.stream()
                .filter(a -> a.getType() == AccountType.EXPENSE)
                .filter(a -> zero(balances.get(a.getId())).signum() > 0)
                .sorted((a, b) -> zero(balances.get(b.getId())).compareTo(zero(balances.get(a.getId()))))
                .toList();
        List<String> expenseLabels = expenseAccounts.stream().map(Account::getName).toList();
        List<Double> expenseData = expenseAccounts.stream()
                .map(a -> millions(zero(balances.get(a.getId()))))
                .toList();
        Map<String, List<Double>> sparkData = sparks(months, monthly, cashOpening,
                receivableTotal, payableTotal, inventoryTotal);

        BigDecimal cashPrevious = cashPosition(months, monthly, cashOpening, lastYm);

        return new Snapshot(today, greeting(username), baseCurrency, company,
                kpi(revenueTotal, t.revenue, p.revenue, false),
                kpi(expenseTotal, t.expense, p.expense, true),
                kpi(profitTotal, t.revenue.subtract(t.expense), p.revenue.subtract(p.expense), false),
                kpi(cashTotal, cashTotal, cashPrevious, false),
                kpi(receivableTotal, ZERO, ZERO, true),
                kpi(payableTotal, ZERO, ZERO, true),
                kpi(inventoryTotal, ZERO, ZERO, false),
                json(labels), json(revenueSeries), json(expenseSeries), json(cashIn), json(cashOut), json(profitSeries),
                json(expenseLabels), json(expenseData),
                new SparksJson(json(sparkData.get("revenue")), json(sparkData.get("expenses")),
                        json(sparkData.get("profit")), json(sparkData.get("cash")),
                        json(sparkData.get("receivable")), json(sparkData.get("payable")),
                        json(sparkData.get("inventory"))),
                activity, journalEntryRepository.count(), alerts, cashIds.size(), margin);

    }

    // ----- KPI helpers -----

    private Kpi kpi(BigDecimal value, BigDecimal current, BigDecimal previous, boolean goodWhenDown) {
        if (previous == null || previous.signum() == 0) {
            return new Kpi(value, ZERO, false, goodWhenDown);
        }
        BigDecimal delta = current.subtract(previous).multiply(BigDecimal.valueOf(100))
                .divide(previous, 2, RoundingMode.HALF_UP);
        boolean good = goodWhenDown ? delta.signum() < 0 : delta.signum() >= 0;
        return new Kpi(value, delta, true, good);
    }

    private BigDecimal totals(List<Account> accounts, Map<Long, BigDecimal> balances, AccountType type) {
        return accounts.stream()
                .filter(a -> a.getType() == type)
                .map(a -> zero(balances.get(a.getId())))
                .reduce(ZERO, BigDecimal::add);
    }

    private BigDecimal sums(Map<Long, BigDecimal> balances, Collection<Long> ids) {
        BigDecimal total = ZERO;
        for (Long id : ids) {
            total = total.add(zero(balances.get(id)));
        }
        return total;
    }

    private BigDecimal balanceOfCode(List<Account> accounts, Map<Long, BigDecimal> balances, String code) {
        return accounts.stream()
                .filter(a -> code.equalsIgnoreCase(a.getCode()))
                .findFirst()
                .map(a -> zero(balances.get(a.getId())))
                .orElse(ZERO);
    }

    private Set<Long> cashAccountIds(List<Account> accounts) {
        Account bankGroup = accounts.stream()
                .filter(a -> "1000".equalsIgnoreCase(a.getCode()))
                .findFirst()
                .orElse(null);
        if (bankGroup == null) {
            return Set.of();
        }
        return accounts.stream()
                .filter(a -> bankGroup.getId().equals(a.getParentId()))
                .map(Account::getId)
                .collect(Collectors.toSet());
    }

    private BigDecimal cashOpening(List<Account> accounts, Set<Long> cashIds) {
        return accounts.stream()
                .filter(a -> cashIds.contains(a.getId()))
                .map(a -> zero(a.getOpeningBalance()))
                .reduce(ZERO, BigDecimal::add);
    }

    // ----- Fiscal year & monthly aggregation -----

    private List<YearMonth> fiscalYearMonths(Company company) {
        String fiscalYearStart = company == null || company.getFiscalYearStart() == null
                ? "July" : company.getFiscalYearStart();
        AccountingService.FiscalYearRange range = accounting.currentFiscalYear(fiscalYearStart);
        List<YearMonth> months = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            months.add(YearMonth.from(range.start().plusMonths(i)));
        }
        return months;
    }

    private Map<YearMonth, Monthly> initMonthly(List<YearMonth> months) {
        Map<YearMonth, Monthly> map = new HashMap<>();
        for (YearMonth ym : months) {
            map.put(ym, new Monthly());
        }
        return map;
    }

    private void aggregate(List<JournalLine> lines, Map<Long, Account> byId,
                           Set<Long> cashIds, Map<YearMonth, Monthly> monthly) {
        for (JournalLine line : lines) {
            JournalEntry entry = line.getEntry();
            if (entry == null || entry.getEntryDate() == null) {
                continue;
            }
            Monthly m = monthly.get(YearMonth.from(entry.getEntryDate()));
            if (m == null) {
                continue;
            }
            Account account = byId.get(line.getAccountId());
            if (account == null) {
                continue;
            }
            if (account.getType() == AccountType.REVENUE) {
                m.revenue = m.revenue.add(line.getCreditValue());
            } else if (account.getType() == AccountType.EXPENSE) {
                m.expense = m.expense.add(line.getDebitValue());
            }
            if (cashIds.contains(line.getAccountId())) {
                m.cashIn = m.cashIn.add(line.getDebitValue());
                m.cashOut = m.cashOut.add(line.getCreditValue());
            }
        }
    }

    private BigDecimal cashPosition(List<YearMonth> months, Map<YearMonth, Monthly> monthly,
                                    BigDecimal opening, YearMonth upTo) {
        BigDecimal position = opening;
        for (YearMonth ym : months) {
            Monthly m = monthly.get(ym);
            position = position.add(m.cashIn).subtract(m.cashOut);
            if (ym.equals(upTo)) {
                return position;
            }
        }
        return position;
    }

    private Map<String, List<Double>> sparks(List<YearMonth> months, Map<YearMonth, Monthly> monthly,
                                             BigDecimal cashOpening, BigDecimal receivable,
                                             BigDecimal payable, BigDecimal inventory) {
        Map<String, List<Double>> sparks = new LinkedHashMap<>();
        sparks.put("revenue", months.stream().map(m -> millions(monthly.get(m).revenue)).toList());
        sparks.put("expenses", months.stream().map(m -> millions(monthly.get(m).expense)).toList());
        sparks.put("profit", months.stream()
                .map(m -> millions(monthly.get(m).revenue.subtract(monthly.get(m).expense))).toList());
        List<Double> cash = new ArrayList<>();
        BigDecimal position = cashOpening;
        for (YearMonth ym : months) {
            Monthly m = monthly.get(ym);
            position = position.add(m.cashIn).subtract(m.cashOut);
            cash.add(millions(position));
        }
        sparks.put("cash", cash);
        sparks.put("receivable", repeat(millions(receivable), months.size()));
        sparks.put("payable", repeat(millions(payable), months.size()));
        sparks.put("inventory", repeat(millions(inventory), months.size()));
        return sparks;
    }

    private List<Double> repeat(double value, int count) {
        List<Double> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(value);
        }
        return list;
    }

    private static double millions(BigDecimal value) {
        return value == null ? 0d : value.doubleValue() / 1_000_000d;
    }

    // ----- Recent activity -----

    private List<ActivityRow> activity(Map<Long, Account> byId, Set<Long> cashIds) {
        List<JournalEntry> recent = journalEntryRepository.findAll(
                        PageRequest.of(0, ACTIVITY_LIMIT,
                                Sort.by(Sort.Direction.DESC, "entryDate").and(Sort.by(Sort.Direction.DESC, "id"))))
                .getContent();
        List<ActivityRow> rows = new ArrayList<>();
        for (JournalEntry entry : recent) {
            rows.add(activityRow(entry, byId, cashIds));
        }
        return rows;
    }

    private ActivityRow activityRow(JournalEntry entry, Map<Long, Account> byId, Set<Long> cashIds) {
        BigDecimal netCash = ZERO;
        BigDecimal biggestCash = ZERO;
        String cashAccount = null;
        boolean hasRevenue = false;
        boolean hasExpense = false;
        String revenueAccount = null;
        String expenseAccount = null;
        for (JournalLine line : entry.getLines()) {
            BigDecimal debit = line.getDebitValue();
            BigDecimal credit = line.getCreditValue();
            Account account = byId.get(line.getAccountId());
            if (cashIds.contains(line.getAccountId())) {
                netCash = netCash.add(debit).subtract(credit);
                BigDecimal cell = debit.add(credit).abs();
                if (cell.compareTo(biggestCash) > 0) {
                    biggestCash = cell;
                    cashAccount = line.getAccountName();
                }
            }
            if (account != null) {
                if (account.getType() == AccountType.REVENUE) {
                    hasRevenue = true;
                    revenueAccount = line.getAccountName();
                } else if (account.getType() == AccountType.EXPENSE) {
                    hasExpense = true;
                    expenseAccount = line.getAccountName();
                }
            }
        }
        boolean inflow;
        BigDecimal amount;
        String accountLabel;
        if (netCash.signum() != 0) {
            inflow = netCash.signum() > 0;
            amount = netCash.abs();
            accountLabel = cashAccount;
        } else if (hasRevenue) {
            inflow = true;
            amount = entry.getTotalCredits() == null ? ZERO : entry.getTotalCredits();
            accountLabel = revenueAccount;
        } else if (hasExpense) {
            inflow = false;
            amount = entry.getTotalDebits() == null ? ZERO : entry.getTotalDebits();
            accountLabel = expenseAccount;
        } else {
            inflow = true;
            amount = entry.getTotalDebits() == null ? ZERO : entry.getTotalDebits();
            accountLabel = entry.getLines().isEmpty() ? null : entry.getLines().get(0).getAccountName();
        }
        return new ActivityRow(entry.getEntryDate(), entry.getId(), entry.getEntryNo(),
                entry.getReference(), entry.getMemo(), accountLabel, amount, inflow,
                entry.getStatus() == null ? "DRAFT" : entry.getStatus().name());
    }

    // ----- Alerts -----

    private List<AlertItem> alerts(List<Account> accounts, Map<Long, BigDecimal> balances,
                                   BigDecimal revenueTotal, BigDecimal monthRevenue,
                                   BigDecimal cashTotal, LocalDate today) {
        Locale locale = LocaleContextHolder.getLocale();
        List<AlertItem> alerts = new ArrayList<>();

        long drafts = journalEntryRepository.countByStatus(JournalEntryStatus.DRAFT);
        if (drafts > 0) {
            alerts.add(new AlertItem("warning", msg("dash.alertDrafts"),
                    msg("dash.alertDraftsDetail", drafts), "/journals?status=DRAFT"));
        }

        long open = periodRepository.countOpen();
        alerts.add(new AlertItem("info", msg("dash.alertOpenPeriods"),
                msg("dash.alertOpenPeriodsDetail", open, today.getYear()), "/accounting/periods"));

        BigDecimal vat = balanceOfCode(accounts, balances, "2101");
        if (vat.signum() > 0) {
            alerts.add(new AlertItem("warning", msg("dash.alertVat"),
                    msg("dash.alertVatDetail", formatAmount(vat)), "/taxes/vat"));
        }

        if (monthRevenue.signum() == 0 && revenueTotal.signum() >= 0) {
            alerts.add(new AlertItem("info", msg("dash.alertNoRevenue"),
                    msg("dash.alertNoRevenueDetail", today.getMonth().getDisplayName(TextStyle.FULL, locale)),
                    "/invoices"));
        }

        if (cashTotal.signum() < 0) {
            alerts.add(new AlertItem("error", msg("dash.alertNegativeCash"),
                    msg("dash.alertNegativeCashDetail", formatAmount(cashTotal)), "/banking/cash"));
        }

        if (alerts.isEmpty()) {
            alerts.add(new AlertItem("success", msg("dash.alertAllClear"),
                    msg("dash.alertAllClearDetail", null), "/"));
        }
        return alerts;
    }

    // ----- Greeting & formatting -----

    private String greeting(String username) {
        String name = "there";
        if (username != null) {
            User user = userRepository.findByUsername(username).orElse(null);
            if (user != null && user.getFirstName() != null && !user.getFirstName().isBlank()) {
                name = user.getFirstName();
            }
        }
        int hour = LocalTime.now().getHour();
        String key = hour < 12 ? "dash.greetingMorning" : hour < 18 ? "dash.greetingAfternoon" : "dash.greetingEvening";
        return msg(key, name);
    }

    private String formatAmount(BigDecimal value) {
        String grouped = NumberFormat.getNumberInstance(LocaleContextHolder.getLocale())
                .format(value == null ? ZERO : value);
        return currency(accounting.getCompany()) + " " + grouped;
    }

    private String currency(Company company) {
        return company != null && company.getCurrencyCode() != null ? company.getCurrencyCode() : "RWF";
    }

    private String msg(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    // ----- Contracts -----

    public record Snapshot(LocalDate asOf, String greeting, String baseCurrency, Company company,
                           Kpi revenue, Kpi expenses, Kpi profit, Kpi cash,
                           Kpi receivables, Kpi payables, Kpi inventory,
                           String labelsJson, String revenueJson, String expensesJson,
                           String cashInJson, String cashOutJson, String profitJson,
                           String expenseLabelsJson, String expenseDataJson, SparksJson sparks,
                           List<ActivityRow> activity, long activityTotal,
                           List<AlertItem> alerts, int cashAccountCount, BigDecimal margin) {}

    public record Kpi(BigDecimal value, BigDecimal delta, boolean hasDelta, boolean good) {}

    public record SparksJson(String revenue, String expenses, String profit, String cash,
                             String receivable, String payable, String inventory) {}

    public record ActivityRow(LocalDate date, Long id, String entryNo, String reference, String memo,
                              String accountName, BigDecimal amount, boolean inflow, String status) {}

    public record AlertItem(String level, String title, String detail, String href) {}

    private static final class Monthly {
        private BigDecimal revenue = ZERO;
        private BigDecimal expense = ZERO;
        private BigDecimal cashIn = ZERO;
        private BigDecimal cashOut = ZERO;

        static Monthly empty() {
            return new Monthly();
        }
    }
}