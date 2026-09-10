/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : AccountingService.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : Chart of accounts, journals, ledger and periods domain service
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.AccountForm;
import com.ntaganira.heritier.ibook.dto.JournalEntryForm;
import com.ntaganira.heritier.ibook.entity.*;
import com.ntaganira.heritier.ibook.enums.AccountType;
import com.ntaganira.heritier.ibook.enums.JournalEntryStatus;
import com.ntaganira.heritier.ibook.enums.JournalEntryType;
import com.ntaganira.heritier.ibook.enums.PeriodStatus;
import com.ntaganira.heritier.ibook.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AccountingService {

    private static final String MODULE = "accounting";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final JournalLineRepository journalLineRepository;
    private final AccountingPeriodRepository periodRepository;
    private final NumberingSequenceRepository numberingSequenceRepository;
    private final CompanyRepository companyRepository;
    private final AuditService auditService;

    public AccountingService(AccountRepository accountRepository,
                             JournalEntryRepository journalEntryRepository,
                             JournalLineRepository journalLineRepository,
                             AccountingPeriodRepository periodRepository,
                             NumberingSequenceRepository numberingSequenceRepository,
                             CompanyRepository companyRepository,
                             AuditService auditService) {
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.journalLineRepository = journalLineRepository;
        this.periodRepository = periodRepository;
        this.numberingSequenceRepository = numberingSequenceRepository;
        this.companyRepository = companyRepository;
        this.auditService = auditService;
    }

    // ----- Accounts -----

    @Transactional(readOnly = true)
    public List<Account> listAccounts() {
        return accountRepository.findAllByOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public List<Account> listAccounts(String q, String type) {
        List<Account> all = accountRepository.findAllByOrderByCodeAsc();
        return all.stream()
                .filter(a -> type == null || type.isBlank() || type.equalsIgnoreCase(a.getType().name()))
                .filter(a -> q == null || q.isBlank()
                        || a.getCode().toLowerCase().contains(q.trim().toLowerCase())
                        || a.getName().toLowerCase().contains(q.trim().toLowerCase()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Account> listActiveAccounts() {
        return accountRepository.findByActiveTrueOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public Account getAccount(Long id) {
        return accountRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean accountCodeExists(String code, Long id) {
        return id == null
                ? accountRepository.existsByCodeIgnoreCase(code)
                : accountRepository.existsByCodeIgnoreCaseAndIdNot(code, id);
    }

    @Transactional
    public Account saveAccount(AccountForm form, Long id) {
        Account account = id == null ? new Account() : accountRepository.findById(id).orElseThrow();
        account.setCode(form.code().trim().toUpperCase(Locale.ROOT));
        account.setName(form.name().trim());
        account.setType(AccountType.valueOf(form.type()));
        if (id != null && form.parentId() != null && form.parentId().equals(id)) {
            account.setParentId(null);
        } else {
            account.setParentId(form.parentId());
        }
        account.setDescription(trimToNull(form.description()));
        account.setOpeningBalance(form.openingBalance() == null ? BigDecimal.ZERO : form.openingBalance());
        account.setActive(form.active());
        Account saved = accountRepository.save(account);
        auditService.log(MODULE, id == null ? "CREATE_ACCOUNT" : "UPDATE_ACCOUNT",
                "account#" + saved.getId(), saved.getCode() + " - " + saved.getName());
        return saved;
    }

    @Transactional
    public void toggleAccount(Long id) {
        Account account = accountRepository.findById(id).orElse(null);
        if (account == null) {
            return;
        }
        account.setActive(!account.isActive());
        accountRepository.save(account);
        auditService.log(MODULE, "TOGGLE_ACCOUNT", "account#" + id,
                account.getCode() + (account.isActive() ? " activated" : " deactivated"));
    }

    // ----- Balances -----

    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> accountBalances() {
        Map<Long, BigDecimal> balances = new HashMap<>();
        Map<Long, BigDecimal> opening = new HashMap<>();
        for (Account a : accountRepository.findAllByOrderByCodeAsc()) {
            BigDecimal ob = a.getOpeningBalance() == null ? BigDecimal.ZERO : a.getOpeningBalance();
            opening.put(a.getId(), ob);
            balances.put(a.getId(), ob);
        }
        for (Object[] row : journalLineRepository.postedTotalsByAccount()) {
            Long accountId = (Long) row[0];
            BigDecimal debits = (BigDecimal) row[1];
            BigDecimal credits = (BigDecimal) row[2];
            Account account = accountRepository.findById(accountId).orElse(null);
            if (account == null) {
                continue;
            }
            BigDecimal ob = opening.getOrDefault(accountId, BigDecimal.ZERO);
            BigDecimal activity = normalSide(account.getType())
                    ? (debits == null ? BigDecimal.ZERO : debits).subtract(credits == null ? BigDecimal.ZERO : credits)
                    : (credits == null ? BigDecimal.ZERO : credits).subtract(debits == null ? BigDecimal.ZERO : debits);
            balances.put(accountId, ob.add(activity));
        }
        return balances;
    }

    private static boolean normalSide(AccountType type) {
        return type == AccountType.ASSET || type == AccountType.EXPENSE;
    }

    // ----- Journals -----

    @Transactional(readOnly = true)
    public Page<JournalEntry> listJournalEntries(String q, String status, Pageable pageable) {
        JournalEntryStatus entryStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                entryStatus = JournalEntryStatus.valueOf(status.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return journalEntryRepository.search(q, entryStatus, pageable);
    }

    @Transactional(readOnly = true)
    public JournalEntry getJournalEntry(Long id) {
        return journalEntryRepository.findById(id).orElse(null);
    }

    @Transactional
    public JournalEntry createJournalEntry(JournalEntryForm form, String username) {
        JournalEntry entry = JournalEntry.builder()
                .entryNo(nextEntryNo())
                .entryDate(form.getEntryDate() == null ? LocalDate.now() : form.getEntryDate())
                .type(JournalEntryType.MANUAL)
                .reference(trimToNull(form.getReference()))
                .memo(trimToNull(form.getMemo()))
                .status(form.isPostNow() ? JournalEntryStatus.POSTED : JournalEntryStatus.DRAFT)
                .totalDebits(form.totalDebits())
                .totalCredits(form.totalCredits())
                .createdBy(username)
                .build();
        int order = 0;
        for (JournalEntryForm.Line lineForm : form.getLines()) {
            if (lineForm.getAccountId() == null
                    || (lineForm.getDebitValue().signum() == 0 && lineForm.getCreditValue().signum() == 0)) {
                continue;
            }
            Account account = accountRepository.findById(lineForm.getAccountId()).orElse(null);
            if (account == null) {
                continue;
            }
            JournalLine line = JournalLine.builder()
                    .accountId(account.getId())
                    .accountCode(account.getCode())
                    .accountName(account.getName())
                    .memo(trimToNull(lineForm.getMemo()))
                    .debit(lineForm.getDebitValue())
                    .credit(lineForm.getCreditValue())
                    .sortOrder(order++)
                    .build();
            entry.addLine(line);
        }
        JournalEntry saved = journalEntryRepository.save(entry);
        auditService.log(MODULE, "CREATE_JOURNAL", "journal#" + saved.getId(),
                saved.getEntryNo() + " " + saved.getStatus().name().toLowerCase(Locale.ROOT));
        return saved;
    }

    private String nextEntryNo() {
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

    // ----- Ledger -----

    @Transactional(readOnly = true)
    public List<JournalLine> postedLinesForAccount(Long accountId) {
        return journalLineRepository.postedByAccount(accountId);
    }

    @Transactional(readOnly = true)
    public List<JournalLine> allPostedLines() {
        return journalLineRepository.postedLines();
    }

    @Transactional(readOnly = true)
    public List<Object[]> postedTotals() {
        return journalLineRepository.postedTotalsByAccount();
    }

    // ----- Periods -----

    @Transactional(readOnly = true)
    public List<AccountingPeriod> listPeriods() {
        return periodRepository.findAllByOrderByStartDateDesc();
    }

    @Transactional(readOnly = true)
    public AccountingPeriod getPeriod(Long id) {
        return periodRepository.findById(id).orElse(null);
    }

    @Transactional
    public int generateYear(int fiscalYear, String fiscalYearStart) {
        int startMonth = fiscalYearStartMonth(fiscalYearStart);
        int created = 0;
        for (int m = 0; m < 12; m++) {
            int monthIndex = ((startMonth - 1) + m) % 12 + 1;
            int year = fiscalYear + ((startMonth - 1) + m) / 12;
            YearMonth yearMonth = YearMonth.of(year, monthIndex);
            LocalDate start = yearMonth.atDay(1);
            LocalDate end = yearMonth.atEndOfMonth();
            String code = "FY" + fiscalYear + "-M" + String.format("%02d", m + 1);
            if (periodRepository.findByCodeIgnoreCase(code).isEmpty()) {
                AccountingPeriod period = AccountingPeriod.builder()
                        .code(code)
                        .label(capitalize(monthName(monthIndex)) + " " + year)
                        .fiscalYear(fiscalYear)
                        .startDate(start)
                        .endDate(end)
                        .status(end.isBefore(LocalDate.now()) ? PeriodStatus.CLOSED : PeriodStatus.OPEN)
                        .build();
                periodRepository.save(period);
                created++;
            }
        }
        if (created > 0) {
            auditService.log(MODULE, "GENERATE_PERIODS", "fiscalYear#" + fiscalYear,
                    "Generated " + created + " accounting periods");
        }
        return created;
    }

    @Transactional
    public void togglePeriod(Long id) {
        AccountingPeriod period = periodRepository.findById(id).orElse(null);
        if (period == null) {
            return;
        }
        period.setStatus(period.getStatus() == PeriodStatus.OPEN ? PeriodStatus.CLOSED : PeriodStatus.OPEN);
        periodRepository.save(period);
        auditService.log(MODULE, "TOGGLE_PERIOD", "period#" + id,
                period.getCode() + " -> " + period.getStatus().name().toLowerCase(Locale.ROOT));
    }

    @Transactional(readOnly = true)
    public long openPeriods() {
        return periodRepository.countOpen();
    }

    @Transactional(readOnly = true)
    public long closedPeriods() {
        return periodRepository.countClosed();
    }

    @Transactional(readOnly = true)
    public long postedEntries() {
        return journalEntryRepository.countByStatus(JournalEntryStatus.POSTED);
    }

    // ----- Fiscal year -----

    @Transactional
    public int closeFiscalYear() {
        List<AccountingPeriod> open = periodRepository.findAllByOrderByStartDateDesc().stream()
                .filter(p -> p.getStatus() == PeriodStatus.OPEN)
                .toList();
        for (AccountingPeriod period : open) {
            period.setStatus(PeriodStatus.CLOSED);
            periodRepository.save(period);
        }
        auditService.log(MODULE, "FISCAL_CLOSE", "fiscalYear#all",
                "Closed " + open.size() + " open accounting periods");
        return open.size();
    }

    @Transactional(readOnly = true)
    public FiscalYearRange currentFiscalYear(String fiscalYearStart) {
        LocalDate today = LocalDate.now();
        int startMonth = fiscalYearStartMonth(fiscalYearStart);
        int startYear = today.getMonthValue() >= startMonth ? today.getYear() : today.getYear() - 1;
        LocalDate start = LocalDate.of(startYear, startMonth == 0 ? 1 : startMonth, 1);
        LocalDate end = LocalDate.of(startYear + 1, startMonth == 0 ? 1 : startMonth, 1).minusDays(1);
        return new FiscalYearRange(startYear, start, end);
    }

    @Transactional(readOnly = true)
    public Company getCompany() {
        return companyRepository.findFirstByOrderByIdAsc().orElse(null);
    }

    private static int fiscalYearStartMonth(String fiscalYearStart) {
        if (fiscalYearStart == null || fiscalYearStart.isBlank()) {
            return 1;
        }
        String[] months = {"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};
        for (int i = 0; i < months.length; i++) {
            if (months[i].equalsIgnoreCase(fiscalYearStart.trim())) {
                return i + 1;
            }
        }
        return 1;
    }

    private static String monthName(int month) {
        return LocalDate.of(2000, month, 1).getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record FiscalYearRange(int startYear, LocalDate start, LocalDate end) {}
}