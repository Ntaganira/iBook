/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : ReportService.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Financial statement reporting built from posted journal lines
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.entity.Account;
import com.ntaganira.heritier.ibook.entity.JournalLine;
import com.ntaganira.heritier.ibook.enums.AccountType;
import com.ntaganira.heritier.ibook.repository.AccountRepository;
import com.ntaganira.heritier.ibook.repository.JournalLineRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {

    private final AccountRepository accountRepository;
    private final JournalLineRepository journalLineRepository;

    public ReportService(AccountRepository accountRepository,
                         JournalLineRepository journalLineRepository) {
        this.accountRepository = accountRepository;
        this.journalLineRepository = journalLineRepository;
    }

    // ----- Shared helpers -----

    private Map<Long, Movement> movementsBetween(LocalDate from, LocalDate to) {
        return toMovements(journalLineRepository.postedTotalsBetween(from, to));
    }

    private Map<Long, Movement> movementsUpTo(LocalDate asOf) {
        return toMovements(journalLineRepository.postedTotalsUpTo(asOf));
    }

    private static Map<Long, Movement> toMovements(List<Object[]> rows) {
        Map<Long, Movement> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            Long accountId = ((Number) row[0]).longValue();
            BigDecimal debit = row[1] == null ? BigDecimal.ZERO : (BigDecimal) row[1];
            BigDecimal credit = row[2] == null ? BigDecimal.ZERO : (BigDecimal) row[2];
            map.put(accountId, new Movement(debit, credit));
        }
        return map;
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** Debit-normal accounts (assets, expenses) increase on the debit side. */
    private static boolean debitNormal(AccountType type) {
        return type == AccountType.ASSET || type == AccountType.EXPENSE;
    }

    private static BigDecimal signed(AccountType type, Movement m) {
        if (m == null) {
            return BigDecimal.ZERO;
        }
        return debitNormal(type)
                ? m.debit().subtract(m.credit())
                : m.credit().subtract(m.debit());
    }

    // ----- Profit and loss -----

    @Transactional(readOnly = true)
    public ProfitAndLoss profitAndLoss(LocalDate from, LocalDate to) {
        Map<Long, Movement> movements = movementsBetween(from, to);
        List<ReportLine> income = new ArrayList<>();
        List<ReportLine> expenses = new ArrayList<>();
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;

        for (Account account : accountRepository.findAllByOrderByCodeAsc()) {
            if (account.getType() != AccountType.REVENUE && account.getType() != AccountType.EXPENSE) {
                continue;
            }
            BigDecimal amount = signed(account.getType(), movements.get(account.getId()));
            if (amount.signum() == 0) {
                continue;
            }
            ReportLine line = new ReportLine(account.getCode(), account.getName(), account.getId(), amount);
            if (account.getType() == AccountType.REVENUE) {
                income.add(line);
                totalIncome = totalIncome.add(amount);
            } else {
                expenses.add(line);
                totalExpense = totalExpense.add(amount);
            }
        }
        return new ProfitAndLoss(from, to, income, expenses, totalIncome, totalExpense,
                totalIncome.subtract(totalExpense));
    }

    /** Net profit for a period, used as current-year earnings on the balance sheet. */
    @Transactional(readOnly = true)
    public BigDecimal netProfitUpTo(LocalDate asOf) {
        Map<Long, Movement> movements = movementsUpTo(asOf);
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        for (Account account : accountRepository.findAllByOrderByCodeAsc()) {
            if (account.getType() == AccountType.REVENUE) {
                income = income.add(signed(AccountType.REVENUE, movements.get(account.getId())));
            } else if (account.getType() == AccountType.EXPENSE) {
                expense = expense.add(signed(AccountType.EXPENSE, movements.get(account.getId())));
            }
        }
        return income.subtract(expense);
    }

    // ----- Balance sheet -----

    @Transactional(readOnly = true)
    public BalanceSheet balanceSheet(LocalDate asOf) {
        Map<Long, Movement> movements = movementsUpTo(asOf);
        List<ReportLine> assets = new ArrayList<>();
        List<ReportLine> liabilities = new ArrayList<>();
        List<ReportLine> equity = new ArrayList<>();
        BigDecimal totalAssets = BigDecimal.ZERO;
        BigDecimal totalLiabilities = BigDecimal.ZERO;
        BigDecimal totalEquity = BigDecimal.ZERO;

        for (Account account : accountRepository.findAllByOrderByCodeAsc()) {
            AccountType type = account.getType();
            if (type == AccountType.REVENUE || type == AccountType.EXPENSE) {
                continue;
            }
            BigDecimal balance = zero(account.getOpeningBalance())
                    .add(signed(type, movements.get(account.getId())));
            if (balance.signum() == 0) {
                continue;
            }
            ReportLine line = new ReportLine(account.getCode(), account.getName(), account.getId(), balance);
            switch (type) {
                case ASSET -> {
                    assets.add(line);
                    totalAssets = totalAssets.add(balance);
                }
                case LIABILITY -> {
                    liabilities.add(line);
                    totalLiabilities = totalLiabilities.add(balance);
                }
                default -> {
                    equity.add(line);
                    totalEquity = totalEquity.add(balance);
                }
            }
        }

        BigDecimal currentEarnings = netProfitUpTo(asOf);
        BigDecimal equityWithEarnings = totalEquity.add(currentEarnings);
        BigDecimal difference = totalAssets.subtract(totalLiabilities).subtract(equityWithEarnings);

        return new BalanceSheet(asOf, assets, liabilities, equity, totalAssets, totalLiabilities,
                totalEquity, currentEarnings, equityWithEarnings,
                totalLiabilities.add(equityWithEarnings), difference);
    }

    // ----- Cash movement -----

    /**
     * Simplified cash movement: every posted line touching a cash or bank account,
     * classified by the type of the accounts on the other side of the entry.
     */
    @Transactional(readOnly = true)
    public CashFlow cashFlow(LocalDate from, LocalDate to) {
        List<Account> accounts = accountRepository.findAllByOrderByCodeAsc();
        Map<Long, Account> byId = new LinkedHashMap<>();
        for (Account a : accounts) {
            byId.put(a.getId(), a);
        }

        List<Long> cashIds = new ArrayList<>();
        BigDecimal opening = BigDecimal.ZERO;
        Map<Long, Movement> upToStart = movementsUpTo(from.minusDays(1));
        for (Account a : accounts) {
            if (isCashAccount(a)) {
                cashIds.add(a.getId());
                opening = opening.add(zero(a.getOpeningBalance())
                        .add(signed(AccountType.ASSET, upToStart.get(a.getId()))));
            }
        }

        BigDecimal operating = BigDecimal.ZERO;
        BigDecimal investing = BigDecimal.ZERO;
        BigDecimal financing = BigDecimal.ZERO;
        Map<String, BigDecimal> byCounterpart = new LinkedHashMap<>();

        List<JournalLine> lines = journalLineRepository.postedLinesBetween(from, to);
        Map<Long, List<JournalLine>> byEntry = new LinkedHashMap<>();
        for (JournalLine line : lines) {
            byEntry.computeIfAbsent(line.getEntry().getId(), k -> new ArrayList<>()).add(line);
        }

        for (List<JournalLine> entryLines : byEntry.values()) {
            BigDecimal cashDelta = BigDecimal.ZERO;
            for (JournalLine line : entryLines) {
                if (cashIds.contains(line.getAccountId())) {
                    cashDelta = cashDelta.add(line.getDebitValue()).subtract(line.getCreditValue());
                }
            }
            if (cashDelta.signum() == 0) {
                continue;
            }
            Account counterpart = null;
            BigDecimal largest = BigDecimal.ZERO;
            for (JournalLine line : entryLines) {
                if (cashIds.contains(line.getAccountId())) {
                    continue;
                }
                BigDecimal magnitude = line.getDebitValue().add(line.getCreditValue());
                if (magnitude.compareTo(largest) > 0) {
                    largest = magnitude;
                    counterpart = byId.get(line.getAccountId());
                }
            }
            Bucket bucket = classify(counterpart);
            switch (bucket) {
                case INVESTING -> investing = investing.add(cashDelta);
                case FINANCING -> financing = financing.add(cashDelta);
                default -> operating = operating.add(cashDelta);
            }
            String label = counterpart == null ? "Unclassified"
                    : counterpart.getCode() + " " + counterpart.getName();
            byCounterpart.merge(label, cashDelta, BigDecimal::add);
        }

        BigDecimal net = operating.add(investing).add(financing);
        List<ReportLine> breakdown = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : byCounterpart.entrySet()) {
            breakdown.add(new ReportLine(null, e.getKey(), null, e.getValue()));
        }
        return new CashFlow(from, to, opening, operating, investing, financing, net,
                opening.add(net), breakdown);
    }

    private static boolean isCashAccount(Account account) {
        if (account.getType() != AccountType.ASSET || account.getCode() == null) {
            return false;
        }
        String code = account.getCode();
        return code.startsWith("10") || code.startsWith("11");
    }

    private static Bucket classify(Account counterpart) {
        if (counterpart == null) {
            return Bucket.OPERATING;
        }
        return switch (counterpart.getType()) {
            case REVENUE, EXPENSE -> Bucket.OPERATING;
            case EQUITY -> Bucket.FINANCING;
            case LIABILITY -> counterpart.getCode() != null && counterpart.getCode().startsWith("24")
                    ? Bucket.FINANCING : Bucket.OPERATING;
            case ASSET -> counterpart.getCode() != null && counterpart.getCode().startsWith("15")
                    ? Bucket.INVESTING : Bucket.OPERATING;
        };
    }

    private enum Bucket { OPERATING, INVESTING, FINANCING }

    // ----- General ledger -----

    @Transactional(readOnly = true)
    public List<LedgerSection> generalLedger(LocalDate from, LocalDate to, Long accountId) {
        Map<Long, Account> byId = new LinkedHashMap<>();
        for (Account a : accountRepository.findAllByOrderByCodeAsc()) {
            byId.put(a.getId(), a);
        }
        Map<Long, Movement> beforeStart = movementsUpTo(from.minusDays(1));

        Map<Long, List<JournalLine>> grouped = new LinkedHashMap<>();
        for (JournalLine line : journalLineRepository.postedLinesBetween(from, to)) {
            if (accountId != null && !accountId.equals(line.getAccountId())) {
                continue;
            }
            grouped.computeIfAbsent(line.getAccountId(), k -> new ArrayList<>()).add(line);
        }

        List<LedgerSection> sections = new ArrayList<>();
        for (Map.Entry<Long, List<JournalLine>> group : grouped.entrySet()) {
            Account account = byId.get(group.getKey());
            if (account == null) {
                continue;
            }
            BigDecimal running = zero(account.getOpeningBalance())
                    .add(signed(account.getType(), beforeStart.get(account.getId())));
            BigDecimal openingBalance = running;
            BigDecimal debits = BigDecimal.ZERO;
            BigDecimal credits = BigDecimal.ZERO;
            List<LedgerRow> rows = new ArrayList<>();
            for (JournalLine line : group.getValue()) {
                BigDecimal debit = line.getDebitValue();
                BigDecimal credit = line.getCreditValue();
                debits = debits.add(debit);
                credits = credits.add(credit);
                running = running.add(debitNormal(account.getType())
                        ? debit.subtract(credit) : credit.subtract(debit));
                rows.add(new LedgerRow(line.getEntry().getEntryDate(), line.getEntry().getEntryNo(),
                        line.getEntry().getReference(), line.getMemo(), debit, credit, running));
            }
            sections.add(new LedgerSection(account.getCode(), account.getName(), account.getId(),
                    openingBalance, debits, credits, running, rows));
        }
        sections.sort((a, b) -> a.accountCode().compareTo(b.accountCode()));
        return sections;
    }

    // ----- Records -----

    public record Movement(BigDecimal debit, BigDecimal credit) {}

    public record ReportLine(String accountCode, String accountName, Long accountId, BigDecimal amount) {}

    public record ProfitAndLoss(LocalDate from, LocalDate to,
                                List<ReportLine> income, List<ReportLine> expenses,
                                BigDecimal totalIncome, BigDecimal totalExpense,
                                BigDecimal netProfit) {}

    public record BalanceSheet(LocalDate asOf,
                               List<ReportLine> assets, List<ReportLine> liabilities, List<ReportLine> equity,
                               BigDecimal totalAssets, BigDecimal totalLiabilities, BigDecimal totalEquity,
                               BigDecimal currentEarnings, BigDecimal equityWithEarnings,
                               BigDecimal totalLiabilitiesAndEquity, BigDecimal difference) {
        public boolean balanced() {
            return difference.signum() == 0;
        }
    }

    public record CashFlow(LocalDate from, LocalDate to,
                           BigDecimal openingCash, BigDecimal operating, BigDecimal investing,
                           BigDecimal financing, BigDecimal netChange, BigDecimal closingCash,
                           List<ReportLine> breakdown) {}

    public record LedgerRow(LocalDate date, String entryNo, String reference, String memo,
                            BigDecimal debit, BigDecimal credit, BigDecimal balance) {}

    public record LedgerSection(String accountCode, String accountName, Long accountId,
                                BigDecimal openingBalance, BigDecimal totalDebits, BigDecimal totalCredits,
                                BigDecimal closingBalance, List<LedgerRow> rows) {}
}
