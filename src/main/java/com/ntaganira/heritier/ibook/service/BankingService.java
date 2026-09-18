/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : BankingService.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Cash and bank account views over posted ledger activity
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
public class BankingService {

    private final AccountRepository accountRepository;
    private final JournalLineRepository journalLineRepository;

    public BankingService(AccountRepository accountRepository,
                          JournalLineRepository journalLineRepository) {
        this.accountRepository = accountRepository;
        this.journalLineRepository = journalLineRepository;
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * Cash-side accounts by chart convention: 10xx is cash on hand, 11xx is bank and
     * mobile money. The 1000 header account itself is excluded — it has no movement.
     */
    public static boolean isBankingAccount(Account account) {
        if (account.getType() != AccountType.ASSET || account.getCode() == null) {
            return false;
        }
        String code = account.getCode();
        if (code.equals("1000")) {
            return false;
        }
        return code.startsWith("10") || code.startsWith("11");
    }

    public static Kind kindOf(Account account) {
        return account.getCode() != null && account.getCode().startsWith("10") ? Kind.CASH : Kind.BANK;
    }

    public enum Kind { CASH, BANK }

    @Transactional(readOnly = true)
    public List<BankAccount> accounts(Kind kind) {
        Map<Long, BigDecimal[]> totals = new LinkedHashMap<>();
        for (Object[] row : journalLineRepository.postedTotalsByAccount()) {
            totals.put(((Number) row[0]).longValue(), new BigDecimal[]{
                    row[1] == null ? BigDecimal.ZERO : (BigDecimal) row[1],
                    row[2] == null ? BigDecimal.ZERO : (BigDecimal) row[2]});
        }
        List<BankAccount> result = new ArrayList<>();
        for (Account account : accountRepository.findAllByOrderByCodeAsc()) {
            if (!isBankingAccount(account)) {
                continue;
            }
            if (kind != null && kindOf(account) != kind) {
                continue;
            }
            BigDecimal[] dc = totals.getOrDefault(account.getId(),
                    new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal balance = zero(account.getOpeningBalance()).add(dc[0]).subtract(dc[1]);
            result.add(new BankAccount(account.getId(), account.getCode(), account.getName(),
                    kindOf(account).name(), zero(account.getOpeningBalance()),
                    dc[0], dc[1], balance, account.isActive()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public BigDecimal totalBalance(Kind kind) {
        BigDecimal total = BigDecimal.ZERO;
        for (BankAccount a : accounts(kind)) {
            total = total.add(a.balance());
        }
        return total;
    }

    /** Every posted movement on a cash or bank account, newest first. */
    @Transactional(readOnly = true)
    public List<BankTransaction> transactions(LocalDate from, LocalDate to, Long accountId) {
        Map<Long, Account> banking = new LinkedHashMap<>();
        for (Account account : accountRepository.findAllByOrderByCodeAsc()) {
            if (isBankingAccount(account)) {
                banking.put(account.getId(), account);
            }
        }
        Map<Long, Account> allAccounts = new LinkedHashMap<>();
        for (Account account : accountRepository.findAllByOrderByCodeAsc()) {
            allAccounts.put(account.getId(), account);
        }

        // Group by entry so the counterpart account can be named as the category.
        Map<Long, List<JournalLine>> byEntry = new LinkedHashMap<>();
        for (JournalLine line : journalLineRepository.postedLinesBetween(from, to)) {
            byEntry.computeIfAbsent(line.getEntry().getId(), k -> new ArrayList<>()).add(line);
        }

        List<BankTransaction> rows = new ArrayList<>();
        for (List<JournalLine> entryLines : byEntry.values()) {
            for (JournalLine line : entryLines) {
                Account account = banking.get(line.getAccountId());
                if (account == null) {
                    continue;
                }
                if (accountId != null && !accountId.equals(line.getAccountId())) {
                    continue;
                }
                String category = null;
                BigDecimal largest = BigDecimal.ZERO;
                for (JournalLine other : entryLines) {
                    if (other.getAccountId().equals(line.getAccountId())) {
                        continue;
                    }
                    BigDecimal magnitude = other.getDebitValue().add(other.getCreditValue());
                    if (magnitude.compareTo(largest) > 0) {
                        largest = magnitude;
                        Account counterpart = allAccounts.get(other.getAccountId());
                        category = counterpart == null ? other.getAccountName()
                                : counterpart.getCode() + " — " + counterpart.getName();
                    }
                }
                rows.add(new BankTransaction(
                        line.getEntry().getEntryDate(),
                        line.getEntry().getEntryNo(),
                        line.getEntry().getId(),
                        line.getEntry().getType().name(),
                        line.getEntry().getReference(),
                        line.getMemo() != null ? line.getMemo() : line.getEntry().getMemo(),
                        account.getCode() + " — " + account.getName(),
                        category,
                        line.getDebitValue(),
                        line.getCreditValue(),
                        line.getDebitValue().subtract(line.getCreditValue())));
            }
        }
        rows.sort((a, b) -> b.date().compareTo(a.date()));
        return rows;
    }

    public record BankAccount(Long accountId, String code, String name, String kind,
                              BigDecimal openingBalance, BigDecimal debits, BigDecimal credits,
                              BigDecimal balance, boolean active) {}

    public record BankTransaction(LocalDate date, String entryNo, Long entryId, String entryType,
                                  String reference, String description, String accountLabel,
                                  String category, BigDecimal moneyIn, BigDecimal moneyOut,
                                  BigDecimal net) {}
}
