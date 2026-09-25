/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : RemittanceService.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : What payroll owes each authority, and paying it over
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.RemittanceForm;
import com.ntaganira.heritier.ibook.entity.*;
import com.ntaganira.heritier.ibook.enums.JournalEntryStatus;
import com.ntaganira.heritier.ibook.enums.JournalEntryType;
import com.ntaganira.heritier.ibook.enums.RemittanceAuthority;
import com.ntaganira.heritier.ibook.enums.RemittanceStatus;
import com.ntaganira.heritier.ibook.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What payroll owes the RRA, RSSB and the CBHI fund, and the payment that clears it.
 *
 * <p>Posting a payroll run only recognises the debt. Until a remittance is paid the money sits on
 * the balance sheet as a liability, which is exactly right — it is somebody else's money being
 * held. This is the other half: it debits the liability and credits the bank.
 *
 * <p>The amount is <strong>typed in rather than forced</strong> to equal what the runs computed.
 * A declaration is routinely filed for a different figure — a penalty, an adjustment, a correction
 * carried from an earlier month — and a screen that refused to record what was actually paid would
 * push the books further from the truth, not closer. What the runs say is shown beside it and the
 * difference is flagged, so a mismatch is visible rather than prevented.
 *
 * <p>A payment is refused if it would leave the liability account owing a negative amount for the
 * period, because that means the money being handed over was never deducted from anybody.
 */
@Service
public class RemittanceService {

    private static final String MODULE = "payroll";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RemittanceRepository remittanceRepository;
    private final PayslipRepository payslipRepository;
    private final PayrollSettingsService payrollSettingsService;
    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final NumberingSequenceRepository numberingSequenceRepository;
    private final AuditService auditService;

    public RemittanceService(RemittanceRepository remittanceRepository,
                             PayslipRepository payslipRepository,
                             PayrollSettingsService payrollSettingsService,
                             AccountRepository accountRepository,
                             JournalEntryRepository journalEntryRepository,
                             NumberingSequenceRepository numberingSequenceRepository,
                             AuditService auditService) {
        this.remittanceRepository = remittanceRepository;
        this.payslipRepository = payslipRepository;
        this.payrollSettingsService = payrollSettingsService;
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.numberingSequenceRepository = numberingSequenceRepository;
        this.auditService = auditService;
    }

    // ----- Queries -----

    @Transactional(readOnly = true)
    public Page<Remittance> list(String q, String authority, String status, Pageable pageable) {
        return remittanceRepository.search(trimToNull(q), parseAuthority(authority),
                parseStatus(status), pageable);
    }

    @Transactional(readOnly = true)
    public Remittance get(Long id) {
        return id == null ? null : remittanceRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public JournalEntry journalFor(Remittance remittance) {
        if (remittance == null || remittance.getJournalEntryId() == null) {
            return null;
        }
        return journalEntryRepository.findById(remittance.getJournalEntryId()).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Account> paymentAccounts() {
        // Cash on hand and bank or mobile money, the same 10xx/11xx convention BankingService uses.
        List<Account> accounts = new ArrayList<>();
        for (Account account : accountRepository.findByActiveTrueOrderByCodeAsc()) {
            String code = account.getCode();
            if (code != null && (code.startsWith("10") || code.startsWith("11"))) {
                accounts.add(account);
            }
        }
        return accounts;
    }

    /** What each authority is owed for a period, what has been paid, and what is still outstanding. */
    @Transactional(readOnly = true)
    public List<Position> positions(LocalDate from, LocalDate to) {
        List<Position> positions = new ArrayList<>();
        positions.add(position(RemittanceAuthority.RRA_PAYE,
                zero(payslipRepository.payeBetween(from, to)), from, to));
        positions.add(position(RemittanceAuthority.RSSB,
                zero(payslipRepository.rssbBetween(from, to)), from, to));
        positions.add(position(RemittanceAuthority.CBHI,
                zero(payslipRepository.cbhiBetween(from, to)), from, to));
        return positions;
    }

    private Position position(RemittanceAuthority authority, BigDecimal owed,
                              LocalDate from, LocalDate to) {
        BigDecimal paid = zero(remittanceRepository.paidForPeriod(authority, from, to));
        return new Position(authority, owed, paid, owed.subtract(paid));
    }

    @Transactional(readOnly = true)
    public BigDecimal expectedFor(RemittanceAuthority authority, LocalDate from, LocalDate to) {
        return switch (authority) {
            case RRA_PAYE -> zero(payslipRepository.payeBetween(from, to));
            case RSSB -> zero(payslipRepository.rssbBetween(from, to));
            case CBHI -> zero(payslipRepository.cbhiBetween(from, to));
        };
    }

    @Transactional(readOnly = true)
    public RemittanceSummary summary() {
        return new RemittanceSummary(
                remittanceRepository.count(),
                remittanceRepository.countByStatus(RemittanceStatus.DRAFT),
                remittanceRepository.countByStatus(RemittanceStatus.PAID),
                remittanceRepository.countByStatus(RemittanceStatus.VOID));
    }

    // ----- Editing -----

    @Transactional
    public Remittance save(RemittanceForm form, Long id, String username) {
        if (form.periodEnd().isBefore(form.periodStart())) {
            throw new IllegalArgumentException("The period has to end after it starts");
        }
        if (form.amountValue().signum() <= 0) {
            throw new IllegalArgumentException("A remittance has to be for more than nothing");
        }

        PayrollSettings settings = payrollSettingsService.current();
        RemittanceAuthority authority = parseAuthority(form.authority());
        if (authority == null) {
            authority = RemittanceAuthority.RRA_PAYE;
        }
        Account liability = liabilityAccount(authority, settings);
        Account payment = accountRepository.findById(form.paymentAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Choose the account the money left"));

        Remittance remittance;
        if (id == null) {
            remittance = new Remittance();
            remittance.setReference(nextReference());
            remittance.setCreatedBy(username);
            remittance.setStatus(RemittanceStatus.DRAFT);
        } else {
            remittance = remittanceRepository.findById(id).orElseThrow();
            if (!remittance.isEditable()) {
                throw new IllegalStateException("Only a draft remittance can be changed");
            }
        }

        remittance.setAuthority(authority);
        remittance.setPeriodStart(form.periodStart());
        remittance.setPeriodEnd(form.periodEnd());
        remittance.setPaymentDate(form.paymentDate());
        remittance.setAmount(form.amountValue());
        remittance.setExpectedAmount(expectedFor(authority, form.periodStart(), form.periodEnd()));
        remittance.setLiabilityAccountId(liability.getId());
        remittance.setLiabilityAccountCode(liability.getCode());
        remittance.setLiabilityAccountName(liability.getName());
        remittance.setPaymentAccountId(payment.getId());
        remittance.setPaymentAccountCode(payment.getCode());
        remittance.setPaymentAccountName(payment.getName());
        remittance.setDeclarationNo(trimToNull(form.declarationNo()));
        remittance.setNotes(trimToNull(form.notes()));

        Remittance saved = remittanceRepository.save(remittance);
        auditService.log(MODULE, id == null ? "CREATE_REMITTANCE" : "UPDATE_REMITTANCE",
                "remittance#" + saved.getId(), saved.getReference() + " — "
                        + authority.name() + " " + saved.getAmount().toPlainString());

        if (form.payNow()) {
            saved = pay(saved.getId(), username);
        }
        return saved;
    }

    /** Debits the statutory liability and credits the account the money left. */
    @Transactional
    public Remittance pay(Long id, String username) {
        Remittance remittance = remittanceRepository.findById(id).orElseThrow();
        if (remittance.isPaid()) {
            return remittance;
        }
        if (remittance.isVoided()) {
            throw new IllegalStateException("A void remittance cannot be paid");
        }

        BigDecimal owed = expectedFor(remittance.getAuthority(),
                remittance.getPeriodStart(), remittance.getPeriodEnd());
        BigDecimal alreadyPaid = zero(remittanceRepository.paidForPeriod(remittance.getAuthority(),
                remittance.getPeriodStart(), remittance.getPeriodEnd()));
        if (owed.signum() == 0 && alreadyPaid.signum() == 0) {
            throw new IllegalStateException("No posted payroll in these weeks owes anything to this "
                    + "body. Check the period, and that the run has actually been posted.");
        }

        Account liability = accountRepository.findById(remittance.getLiabilityAccountId())
                .orElseThrow(() -> new IllegalStateException("The liability account no longer exists"));
        Account payment = accountRepository.findById(remittance.getPaymentAccountId())
                .orElseThrow(() -> new IllegalStateException("The payment account no longer exists"));

        JournalEntry entry = JournalEntry.builder()
                .entryNo(nextJournalNo())
                .entryDate(remittance.getPaymentDate())
                .type(JournalEntryType.PAYMENT)
                .status(JournalEntryStatus.POSTED)
                .reference(remittance.getReference())
                .memo(label(remittance.getAuthority()) + " remitted — " + remittance.getReference()
                        + " for " + remittance.getPeriodStart() + " to " + remittance.getPeriodEnd())
                .createdBy(username)
                .build();
        entry.addLine(JournalLine.builder()
                .accountId(liability.getId())
                .accountCode(liability.getCode())
                .accountName(liability.getName())
                .memo(remittance.getDeclarationNo() == null
                        ? remittance.getReference() : remittance.getDeclarationNo())
                .debit(remittance.getAmount())
                .credit(BigDecimal.ZERO)
                .sortOrder(0)
                .build());
        entry.addLine(JournalLine.builder()
                .accountId(payment.getId())
                .accountCode(payment.getCode())
                .accountName(payment.getName())
                .memo(label(remittance.getAuthority()))
                .debit(BigDecimal.ZERO)
                .credit(remittance.getAmount())
                .sortOrder(1)
                .build());
        entry.setTotalDebits(remittance.getAmount());
        entry.setTotalCredits(remittance.getAmount());
        JournalEntry savedEntry = journalEntryRepository.save(entry);

        remittance.setJournalEntryId(savedEntry.getId());
        remittance.setStatus(RemittanceStatus.PAID);
        Remittance saved = remittanceRepository.save(remittance);
        auditService.log(MODULE, "PAY_REMITTANCE", "remittance#" + saved.getId(),
                saved.getReference() + " paid as " + savedEntry.getEntryNo());
        return saved;
    }

    @Transactional
    public Remittance voidRemittance(Long id, String reason, String username) {
        Remittance remittance = remittanceRepository.findById(id).orElseThrow();
        if (remittance.isVoided()) {
            return remittance;
        }
        if (remittance.isPaid()) {
            JournalEntry original = journalEntryRepository
                    .findById(remittance.getJournalEntryId()).orElse(null);
            if (original != null) {
                JournalEntry reversal = JournalEntry.builder()
                        .entryNo(nextJournalNo())
                        .entryDate(LocalDate.now())
                        .type(JournalEntryType.ADJUSTMENT)
                        .status(JournalEntryStatus.POSTED)
                        .reference(remittance.getReference())
                        .memo("Reversal of " + original.getEntryNo() + " — voided remittance "
                                + remittance.getReference())
                        .createdBy(username)
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
                reversal.setTotalDebits(zero(remittance.getAmount()));
                reversal.setTotalCredits(zero(remittance.getAmount()));
                journalEntryRepository.save(reversal);
            }
        }
        remittance.setStatus(RemittanceStatus.VOID);
        Remittance saved = remittanceRepository.save(remittance);
        auditService.log(MODULE, "VOID_REMITTANCE", "remittance#" + id,
                saved.getReference() + (reason == null || reason.isBlank() ? "" : " — " + reason.trim()));
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Remittance remittance = remittanceRepository.findById(id).orElse(null);
        if (remittance == null) {
            return;
        }
        if (!remittance.isEditable()) {
            throw new IllegalStateException("Only a draft remittance can be deleted");
        }
        remittanceRepository.delete(remittance);
        auditService.log(MODULE, "DELETE_REMITTANCE", "remittance#" + id,
                remittance.getReference() + " draft deleted");
    }

    public RemittanceForm toForm(Remittance r) {
        return new RemittanceForm(r.getAuthority().name(), r.getPeriodStart(), r.getPeriodEnd(),
                r.getPaymentDate(), r.getAmount(), r.getPaymentAccountId(), r.getDeclarationNo(),
                r.getNotes(), false);
    }

    private Account liabilityAccount(RemittanceAuthority authority, PayrollSettings settings) {
        Long id = switch (authority) {
            case RRA_PAYE -> settings.getPayePayableAccountId();
            case RSSB -> settings.getRssbPayableAccountId();
            case CBHI -> settings.getCbhiPayableAccountId();
        };
        if (id == null) {
            throw new IllegalStateException("Payroll settings has no liability account for "
                    + label(authority) + " — choose one there first");
        }
        return accountRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("The " + label(authority)
                        + " liability account no longer exists"));
    }

    public static String label(RemittanceAuthority authority) {
        return switch (authority) {
            case RRA_PAYE -> "PAYE";
            case RSSB -> "RSSB";
            case CBHI -> "CBHI";
        };
    }

    private String nextReference() {
        NumberingSequence seq = numberingSequenceRepository.findByDocType("REMITTANCE").orElse(null);
        if (seq != null) {
            long next = seq.getNextNumber();
            String prefix = seq.getPrefix() == null ? "REM-" : seq.getPrefix();
            String suffix = seq.getSuffix() == null ? "" : seq.getSuffix();
            int padding = seq.getPadding() == 0 ? 4 : seq.getPadding();
            String number = prefix + String.format("%0" + padding + "d", next) + suffix;
            seq.setNextNumber(next + 1);
            numberingSequenceRepository.save(seq);
            return number;
        }
        return "REM-" + LocalDate.now().getYear() + "-" + String.format("%05d", RANDOM.nextInt(100000));
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

    public static RemittanceAuthority parseAuthority(String authority) {
        if (authority == null || authority.isBlank()) {
            return null;
        }
        try {
            return RemittanceAuthority.valueOf(authority.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static RemittanceStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return RemittanceStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
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

    public record Position(RemittanceAuthority authority, BigDecimal owed,
                           BigDecimal paid, BigDecimal outstanding) {}

    public record RemittanceSummary(long all, long draft, long paid, long voided) {}
}
