/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : RecurringJournalService.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Journal entries that repeat on a cycle, and the sweep that raises them
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.RecurringJournalForm;
import com.ntaganira.heritier.ibook.entity.Account;
import com.ntaganira.heritier.ibook.entity.JournalEntry;
import com.ntaganira.heritier.ibook.entity.JournalLine;
import com.ntaganira.heritier.ibook.entity.NumberingSequence;
import com.ntaganira.heritier.ibook.entity.RecurringJournal;
import com.ntaganira.heritier.ibook.entity.RecurringJournalLine;
import com.ntaganira.heritier.ibook.enums.JournalEntryStatus;
import com.ntaganira.heritier.ibook.enums.JournalEntryType;
import com.ntaganira.heritier.ibook.enums.RecurrenceFrequency;
import com.ntaganira.heritier.ibook.enums.RecurringJournalStatus;
import com.ntaganira.heritier.ibook.repository.AccountRepository;
import com.ntaganira.heritier.ibook.repository.JournalEntryRepository;
import com.ntaganira.heritier.ibook.repository.NumberingSequenceRepository;
import com.ntaganira.heritier.ibook.repository.RecurringJournalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A recurring journal is a <strong>template</strong> for an entry that repeats — depreciation of a
 * lease, a monthly accrual, an amortised prepayment. The schedule itself posts nothing; each
 * occurrence is a real {@link JournalEntry} dated the day it was owed.
 *
 * <p>Occurrences are raised as <strong>drafts</strong> unless the schedule opts into auto-posting.
 * That default is deliberate: an entry that repeats unchanged is exactly the kind that goes on
 * being wrong for a year before anybody looks at it, and a draft costs one click to post.
 *
 * <p>The rule that matters is <strong>balance</strong>. A schedule cannot start unless its debits
 * equal its credits, and the totals are checked <em>again</em> as each entry is written rather than
 * trusted from the row — an account deactivated or deleted between one month and the next would
 * otherwise have the sweep quietly writing a one-sided entry into the ledger every cycle.
 */
@Service
public class RecurringJournalService {

    private static final Logger LOG = LoggerFactory.getLogger(RecurringJournalService.class);
    private static final String MODULE = "accounting";
    private static final int MAX_CATCHUP = 24;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RecurringJournalRepository recurringJournalRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final AccountRepository accountRepository;
    private final NumberingSequenceRepository numberingSequenceRepository;
    private final AuditService auditService;

    public RecurringJournalService(RecurringJournalRepository recurringJournalRepository,
                                   JournalEntryRepository journalEntryRepository,
                                   AccountRepository accountRepository,
                                   NumberingSequenceRepository numberingSequenceRepository,
                                   AuditService auditService) {
        this.recurringJournalRepository = recurringJournalRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.accountRepository = accountRepository;
        this.numberingSequenceRepository = numberingSequenceRepository;
        this.auditService = auditService;
    }

    // ----- Queries -----

    @Transactional(readOnly = true)
    public Page<RecurringJournal> list(String q, String status, Pageable pageable) {
        return recurringJournalRepository.search(trimToNull(q), parseStatus(status), pageable);
    }

    @Transactional(readOnly = true)
    public RecurringJournal get(Long id) {
        return id == null ? null : recurringJournalRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public ScheduleSummary summary() {
        return new ScheduleSummary(recurringJournalRepository.count(),
                recurringJournalRepository.countByStatus(RecurringJournalStatus.DRAFT),
                recurringJournalRepository.countByStatus(RecurringJournalStatus.ACTIVE),
                recurringJournalRepository.countByStatus(RecurringJournalStatus.PAUSED),
                recurringJournalRepository.countByStatus(RecurringJournalStatus.COMPLETED),
                recurringJournalRepository.countByStatus(RecurringJournalStatus.CANCELLED),
                recurringJournalRepository.findDue(LocalDate.now()).size());
    }

    @Transactional(readOnly = true)
    public List<Account> postableAccounts() {
        return accountRepository.findByActiveTrueOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public boolean nameExists(String name, Long id) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return id == null
                ? recurringJournalRepository.existsByNameIgnoreCase(name.trim())
                : recurringJournalRepository.existsByNameIgnoreCaseAndIdNot(name.trim(), id);
    }

    /** The entries a schedule has actually raised, newest first. */
    @Transactional(readOnly = true)
    public List<JournalEntry> entriesFrom(RecurringJournal schedule) {
        return schedule == null ? List.of()
                : journalEntryRepository.findByReferenceOrderByEntryDateDescIdDesc(
                        referenceFor(schedule));
    }

    // ----- Create / update -----

    @Transactional
    public RecurringJournal save(RecurringJournalForm form, Long id, String username) {
        RecurringJournal schedule;
        if (id == null) {
            schedule = new RecurringJournal();
            schedule.setCreatedBy(username);
            schedule.setStatus(RecurringJournalStatus.DRAFT);
        } else {
            schedule = recurringJournalRepository.findById(id).orElseThrow();
            if (!schedule.isEditable()) {
                throw new IllegalStateException("A cancelled schedule cannot be edited");
            }
        }

        if (trimToNull(form.getName()) == null) {
            throw new IllegalArgumentException("A schedule needs a name");
        }
        if (form.getStartDate() == null) {
            throw new IllegalArgumentException("Say which day the first entry falls on");
        }
        if (form.getEndDate() != null && form.getEndDate().isBefore(form.getStartDate())) {
            throw new IllegalArgumentException("The end date falls before the start date");
        }
        if (form.getMaxOccurrences() != null && form.getMaxOccurrences() < 1) {
            throw new IllegalArgumentException("A cap of fewer than one entry stops nothing");
        }

        schedule.getLines().clear();
        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        int order = 0;

        for (RecurringJournalForm.Line lineForm : form.getLines()) {
            if (lineForm == null || !lineForm.isFilled()) {
                continue;
            }
            Account account = accountRepository.findById(lineForm.getAccountId()).orElse(null);
            if (account == null) {
                throw new IllegalArgumentException("One of the lines names an account that no "
                        + "longer exists");
            }
            BigDecimal debit = lineForm.debitValue().setScale(2, RoundingMode.HALF_UP);
            BigDecimal credit = lineForm.creditValue().setScale(2, RoundingMode.HALF_UP);
            if (debit.signum() < 0 || credit.signum() < 0) {
                throw new IllegalArgumentException("A line cannot carry a negative figure. Put it "
                        + "on the other side instead.");
            }
            if (debit.signum() > 0 && credit.signum() > 0) {
                throw new IllegalArgumentException("Line " + (order + 1) + " on " + account.getCode()
                        + " is both a debit and a credit. A line is one or the other.");
            }

            schedule.addLine(RecurringJournalLine.builder()
                    .accountId(account.getId())
                    .accountCode(account.getCode())
                    .accountName(account.getName())
                    .memo(trimToNull(lineForm.getMemo()))
                    .debit(debit)
                    .credit(credit)
                    .sortOrder(order++)
                    .build());
            debits = debits.add(debit);
            credits = credits.add(credit);
        }

        if (schedule.getLines().size() < 2) {
            throw new IllegalArgumentException("An entry needs at least two lines");
        }

        schedule.setName(form.getName().trim());
        schedule.setDescription(trimToNull(form.getDescription()));
        schedule.setFrequency(parseFrequency(form.getFrequency()));
        schedule.setStartDate(form.getStartDate());
        schedule.setEndDate(form.getEndDate());
        schedule.setMaxOccurrences(form.getMaxOccurrences());
        schedule.setAutoPost(form.autoPostValue());
        schedule.setReference(trimToNull(form.getReference()));
        schedule.setMemo(trimToNull(form.getMemo()));
        schedule.setNotes(trimToNull(form.getNotes()));
        schedule.setTotalDebits(debits);
        schedule.setTotalCredits(credits);
        if (schedule.getNextRunDate() == null || schedule.getOccurrencesGenerated() == 0) {
            schedule.setNextRunDate(form.getStartDate());
        }

        RecurringJournal saved = recurringJournalRepository.save(schedule);
        auditService.log(MODULE, id == null ? "CREATE_RECURRING_JOURNAL"
                : "UPDATE_RECURRING_JOURNAL", "recurringJournal#" + saved.getId(), saved.getName());

        if (form.isActivateNow() && saved.isDraft()) {
            saved = activate(saved.getId());
        }
        return saved;
    }

    // ----- Lifecycle -----

    @Transactional
    public RecurringJournal activate(Long id) {
        RecurringJournal schedule = recurringJournalRepository.findById(id).orElseThrow();
        if (schedule.isCancelled()) {
            throw new IllegalStateException("A cancelled schedule cannot be started");
        }
        if (schedule.isRunning()) {
            throw new IllegalStateException("This schedule is already running");
        }
        if (schedule.getLines().isEmpty()) {
            throw new IllegalStateException("A schedule with no lines has nothing to post");
        }
        if (!schedule.isBalanced()) {
            throw new IllegalStateException("Debits and credits do not agree — the difference is "
                    + schedule.getDifference().abs().toPlainString()
                    + ". A schedule that does not balance would write a broken entry every cycle.");
        }
        schedule.setStatus(RecurringJournalStatus.ACTIVE);
        if (schedule.getNextRunDate() == null) {
            schedule.setNextRunDate(schedule.getStartDate());
        }
        return log(recurringJournalRepository.save(schedule), "ACTIVATE_RECURRING_JOURNAL");
    }

    @Transactional
    public RecurringJournal pause(Long id) {
        RecurringJournal schedule = recurringJournalRepository.findById(id).orElseThrow();
        if (!schedule.isRunning()) {
            throw new IllegalStateException("Only a running schedule can be paused");
        }
        schedule.setStatus(RecurringJournalStatus.PAUSED);
        return log(recurringJournalRepository.save(schedule), "PAUSE_RECURRING_JOURNAL");
    }

    /**
     * Stopping leaves every entry already raised exactly where it is, posted ones included. That
     * is the right treatment — an entry in the ledger is a fact — but it means a schedule that ran
     * wrong has to be corrected entry by entry.
     */
    @Transactional
    public RecurringJournal cancel(Long id, String reason) {
        RecurringJournal schedule = recurringJournalRepository.findById(id).orElseThrow();
        if (schedule.isCancelled()) {
            throw new IllegalStateException("This schedule is already cancelled");
        }
        schedule.setStatus(RecurringJournalStatus.CANCELLED);
        schedule.setStoppedReason(trimToNull(reason));
        return log(recurringJournalRepository.save(schedule), "CANCEL_RECURRING_JOURNAL");
    }

    @Transactional
    public void delete(Long id) {
        RecurringJournal schedule = recurringJournalRepository.findById(id).orElse(null);
        if (schedule == null) {
            return;
        }
        if (schedule.getOccurrencesGenerated() > 0) {
            throw new IllegalArgumentException("This schedule has already raised "
                    + schedule.getOccurrencesGenerated()
                    + " entries. Cancel it instead of deleting it.");
        }
        recurringJournalRepository.delete(schedule);
        auditService.log(MODULE, "DELETE_RECURRING_JOURNAL", "recurringJournal#" + id,
                schedule.getName());
    }

    // ----- Generation -----

    /** Raises the next occurrence by hand, for a schedule somebody does not want to wait on. */
    @Transactional
    public JournalEntry runNow(Long id, String username) {
        RecurringJournal schedule = recurringJournalRepository.findById(id).orElseThrow();
        if (!schedule.isRunning()) {
            throw new IllegalStateException("Only a running schedule raises entries");
        }
        if (schedule.isExhausted()) {
            throw new IllegalStateException("This schedule has run out of time or occurrences");
        }
        LocalDate owed = schedule.getNextRunDate() == null
                ? LocalDate.now() : schedule.getNextRunDate();
        JournalEntry entry = generateOne(schedule, owed, username);
        recurringJournalRepository.save(schedule);
        return entry;
    }

    /**
     * The nightly sweep. Each schedule catches up one occurrence at a time so a schedule left
     * paused for months raises the entries it owed on the dates it owed them, capped so a start
     * date typed as 2019 cannot write hundreds of entries in one night.
     */
    @Transactional
    public RunReport runDue(LocalDate today, String username) {
        List<RecurringJournal> due = recurringJournalRepository.findDue(today);
        int schedules = 0;
        int entries = 0;
        List<String> failures = new ArrayList<>();

        for (RecurringJournal schedule : due) {
            if (schedule.getLines().isEmpty()) {
                failures.add(schedule.getName());
                continue;
            }
            int before = entries;
            try {
                int guard = 0;
                while (schedule.getStatus() == RecurringJournalStatus.ACTIVE
                        && !schedule.isExhausted()
                        && schedule.getNextRunDate() != null
                        && !schedule.getNextRunDate().isAfter(today)
                        && guard < MAX_CATCHUP) {
                    generateOne(schedule, schedule.getNextRunDate(), username);
                    entries++;
                    guard++;
                }
                recurringJournalRepository.save(schedule);
            } catch (RuntimeException ex) {
                LOG.warn("Recurring journal {} failed to generate: {}",
                        schedule.getName(), ex.getMessage());
                failures.add(schedule.getName() + " — " + ex.getMessage());
            }
            if (entries > before) {
                schedules++;
            }
        }
        return new RunReport(schedules, entries, failures);
    }

    /**
     * Writes one entry dated the day it was owed.
     *
     * <p>Accounts are read from the chart again rather than taken from the schedule's remembered
     * code and name, and the totals are re-added from the lines actually written. A schedule that
     * no longer balances is refused here, not silently posted, because a one-sided entry in the
     * ledger is far harder to find later than a schedule that stopped.
     */
    private JournalEntry generateOne(RecurringJournal schedule, LocalDate owed, String username) {
        JournalEntry entry = JournalEntry.builder()
                .entryNo(nextEntryNo())
                .entryDate(owed)
                .type(JournalEntryType.MANUAL)
                .reference(referenceFor(schedule))
                .memo(schedule.getMemo() == null ? schedule.getName() : schedule.getMemo())
                .status(schedule.isAutoPost() ? JournalEntryStatus.POSTED : JournalEntryStatus.DRAFT)
                .createdBy(username)
                .build();

        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        int order = 0;
        for (RecurringJournalLine line : schedule.getLines()) {
            Account account = accountRepository.findById(line.getAccountId()).orElse(null);
            if (account == null) {
                throw new IllegalStateException("Account " + line.getAccountCode()
                        + " on this schedule no longer exists");
            }
            entry.addLine(JournalLine.builder()
                    .accountId(account.getId())
                    .accountCode(account.getCode())
                    .accountName(account.getName())
                    .memo(line.getMemo())
                    .debit(line.getDebitValue())
                    .credit(line.getCreditValue())
                    .sortOrder(order++)
                    .build());
            debits = debits.add(line.getDebitValue());
            credits = credits.add(line.getCreditValue());
        }

        if (debits.compareTo(credits) != 0 || debits.signum() == 0) {
            throw new IllegalStateException("This schedule no longer balances — debits "
                    + debits.toPlainString() + " against credits " + credits.toPlainString());
        }
        entry.setTotalDebits(debits);
        entry.setTotalCredits(credits);

        JournalEntry saved = journalEntryRepository.save(entry);

        schedule.setOccurrencesGenerated(schedule.getOccurrencesGenerated() + 1);
        schedule.setLastRunDate(owed);
        schedule.setLastEntryId(saved.getId());
        schedule.setLastEntryNo(saved.getEntryNo());
        schedule.setNextRunDate(schedule.getFrequency().next(owed));
        if (schedule.isExhausted()) {
            schedule.setStatus(RecurringJournalStatus.COMPLETED);
        }

        auditService.log(MODULE, "GENERATE_RECURRING_JOURNAL", "journal#" + saved.getId(),
                schedule.getName() + " → " + saved.getEntryNo() + " "
                        + saved.getStatus().name().toLowerCase(Locale.ROOT));
        return saved;
    }

    /** Ties every entry a schedule raised back to it, so the history on the page is findable. */
    private static String referenceFor(RecurringJournal schedule) {
        return schedule.getReference() == null || schedule.getReference().isBlank()
                ? "RJ-" + schedule.getId()
                : schedule.getReference();
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

    // ----- Helpers -----

    private RecurringJournal log(RecurringJournal schedule, String action) {
        auditService.log(MODULE, action, "recurringJournal#" + schedule.getId(),
                schedule.getName());
        return schedule;
    }

    public static RecurringJournalStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return RecurringJournalStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static RecurrenceFrequency parseFrequency(String frequency) {
        if (frequency == null || frequency.isBlank()) {
            return RecurrenceFrequency.MONTHLY;
        }
        try {
            return RecurrenceFrequency.valueOf(frequency.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return RecurrenceFrequency.MONTHLY;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record RunReport(int schedules, int entries, List<String> failures) {

        public boolean isEmpty() {
            return entries == 0;
        }

        public boolean hasFailures() {
            return !failures.isEmpty();
        }
    }

    public record ScheduleSummary(long all, long draft, long active, long paused,
                                  long completed, long cancelled, long dueNow) {}
}
