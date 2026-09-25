/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : BankReconciliationService.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Agreeing a bank statement with the ledger
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.BankReconciliationForm;
import com.ntaganira.heritier.ibook.entity.Account;
import com.ntaganira.heritier.ibook.entity.BankReconciliation;
import com.ntaganira.heritier.ibook.entity.BankStatementLine;
import com.ntaganira.heritier.ibook.entity.JournalLine;
import com.ntaganira.heritier.ibook.enums.ReconciliationStatus;
import com.ntaganira.heritier.ibook.repository.AccountRepository;
import com.ntaganira.heritier.ibook.repository.BankReconciliationRepository;
import com.ntaganira.heritier.ibook.repository.BankStatementLineRepository;
import com.ntaganira.heritier.ibook.repository.JournalLineRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Agreeing a bank statement with what the books say.
 *
 * <p><strong>Nothing here posts.</strong> Reconciling changes no figure anywhere — it establishes
 * that the ledger and the bank agree, and where they do not, why. A reconciliation that quietly
 * adjusted the books to match the bank would destroy the exact disagreement it exists to find.
 *
 * <p>The arithmetic is the standard two-sided proof, and both sides must arrive at the same
 * number before a reconciliation can be completed:
 *
 * <pre>
 *   closing balance per the bank statement
 *     + money the books have received that the statement does not show   (deposits in transit)
 *     − money the books have paid out that the statement does not show   (unpresented payments)
 *   = adjusted bank balance
 *
 *   balance per the books at the statement date
 *     + money the statement shows arriving that the books do not know of
 *     − money the statement shows leaving that the books do not know of
 *   = adjusted book balance
 * </pre>
 *
 * <p>The second list is the one that matters: bank charges, interest and direct debits the company
 * never entered. They are shown and named, and <strong>this service will not enter them</strong> —
 * inventing a journal entry from a line on a statement would be posting to the ledger on the
 * strength of somebody else's document. They have to be entered as journal entries first, and
 * until they are the reconciliation will not complete, which is the correct outcome rather than an
 * inconvenience.
 *
 * <p>A ledger line matched by a completed reconciliation is never offered again: the same movement
 * agreed twice would hide a real difference.
 */
@Service
public class BankReconciliationService {

    private static final String MODULE = "banking";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BankReconciliationRepository bankReconciliationRepository;
    private final BankStatementLineRepository bankStatementLineRepository;
    private final JournalLineRepository journalLineRepository;
    private final AccountRepository accountRepository;
    private final AuditService auditService;

    public BankReconciliationService(BankReconciliationRepository bankReconciliationRepository,
                                     BankStatementLineRepository bankStatementLineRepository,
                                     JournalLineRepository journalLineRepository,
                                     AccountRepository accountRepository,
                                     AuditService auditService) {
        this.bankReconciliationRepository = bankReconciliationRepository;
        this.bankStatementLineRepository = bankStatementLineRepository;
        this.journalLineRepository = journalLineRepository;
        this.accountRepository = accountRepository;
        this.auditService = auditService;
    }

    // ----- Queries -----

    @Transactional(readOnly = true)
    public Page<BankReconciliation> list(String q, Long accountId, String status, Pageable pageable) {
        return bankReconciliationRepository.search(trimToNull(q), accountId,
                parseStatus(status), pageable);
    }

    @Transactional(readOnly = true)
    public BankReconciliation get(Long id) {
        return id == null ? null : bankReconciliationRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Account> bankAccounts() {
        List<Account> accounts = new ArrayList<>();
        for (Account account : accountRepository.findByActiveTrueOrderByCodeAsc()) {
            if (BankingService.isBankingAccount(account)) {
                accounts.add(account);
            }
        }
        return accounts;
    }

    @Transactional(readOnly = true)
    public ReconciliationSummary summary() {
        return new ReconciliationSummary(
                bankReconciliationRepository.count(),
                bankReconciliationRepository.countByStatus(ReconciliationStatus.DRAFT),
                bankReconciliationRepository.countByStatus(ReconciliationStatus.COMPLETED),
                bankReconciliationRepository.countByStatus(ReconciliationStatus.VOID));
    }

    // ----- Editing -----

    @Transactional
    public BankReconciliation save(BankReconciliationForm form, Long id, String username) {
        if (form.getAccountId() == null) {
            throw new IllegalArgumentException("Choose the account this statement is for");
        }
        if (form.getStatementDate() == null) {
            throw new IllegalArgumentException("A statement needs the date it runs to");
        }
        Account account = accountRepository.findById(form.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("That account no longer exists"));
        if (!BankingService.isBankingAccount(account)) {
            throw new IllegalArgumentException(account.getCode() + " is not a cash or bank account. "
                    + "Only 10xx and 11xx accounts hold money a bank can send a statement about.");
        }

        BankReconciliation reconciliation;
        if (id == null) {
            reconciliation = new BankReconciliation();
            reconciliation.setReference(nextReference());
            reconciliation.setCreatedBy(username);
            reconciliation.setStatus(ReconciliationStatus.DRAFT);
        } else {
            reconciliation = bankReconciliationRepository.findById(id).orElseThrow();
            if (!reconciliation.isEditable()) {
                throw new IllegalStateException("Only a draft reconciliation can be changed");
            }
            reconciliation.getLines().clear();
        }

        reconciliation.setAccountId(account.getId());
        reconciliation.setAccountCode(account.getCode());
        reconciliation.setAccountName(account.getName());
        reconciliation.setStatementDate(form.getStatementDate());
        reconciliation.setOpeningBalance(form.openingValue());
        reconciliation.setClosingBalance(form.closingValue());
        reconciliation.setNotes(trimToNull(form.getNotes()));

        int order = 0;
        for (BankReconciliationForm.Line line : form.filledLines()) {
            reconciliation.addLine(BankStatementLine.builder()
                    .lineDate(line.getLineDate())
                    .description(trimToNull(line.getDescription()))
                    .reference(trimToNull(line.getReference()))
                    .moneyIn(line.inValue())
                    .moneyOut(line.outValue())
                    .sortOrder(order++)
                    .build());
        }

        BankReconciliation saved = bankReconciliationRepository.save(reconciliation);
        auditService.log(MODULE, id == null ? "CREATE_RECONCILIATION" : "UPDATE_RECONCILIATION",
                "bankReconciliation#" + saved.getId(),
                saved.getReference() + " — " + saved.getAccountCode());
        return saved;
    }

    /** Asserts that a statement line and a ledger line are the same movement of money. */
    @Transactional
    public void match(Long reconciliationId, Long statementLineId, Long journalLineId) {
        BankReconciliation reconciliation = bankReconciliationRepository
                .findById(reconciliationId).orElseThrow();
        if (!reconciliation.isEditable()) {
            throw new IllegalStateException("Only a draft reconciliation can be changed");
        }
        BankStatementLine statementLine = bankStatementLineRepository
                .findById(statementLineId).orElseThrow();

        if (journalLineId == null) {
            statementLine.setMatchedLineId(null);
            statementLine.setMatchedEntryNo(null);
            bankStatementLineRepository.save(statementLine);
            return;
        }

        JournalLine journalLine = journalLineRepository.findById(journalLineId).orElseThrow();
        if (!reconciliation.getAccountId().equals(journalLine.getAccountId())) {
            throw new IllegalArgumentException("That entry is not on this account");
        }

        // The two must agree on amount and direction, or they are not the same movement. Matching
        // a 50,000 payment to a 5,000 one would make the reconciliation balance while leaving the
        // books wrong by 45,000, which is precisely the fault this is meant to find.
        BigDecimal ledgerNet = journalLine.getDebitValue().subtract(journalLine.getCreditValue());
        if (ledgerNet.compareTo(statementLine.getNet()) != 0) {
            throw new IllegalArgumentException("Those are not the same movement — the statement "
                    + "says " + statementLine.getNet().toPlainString() + " and the entry says "
                    + ledgerNet.toPlainString());
        }

        for (BankStatementLine other : reconciliation.getLines()) {
            if (!other.getId().equals(statementLineId) && journalLineId.equals(other.getMatchedLineId())) {
                throw new IllegalArgumentException("That entry is already matched to another line "
                        + "on this statement");
            }
        }

        statementLine.setMatchedLineId(journalLine.getId());
        statementLine.setMatchedEntryNo(journalLine.getEntry().getEntryNo());
        bankStatementLineRepository.save(statementLine);
        auditService.log(MODULE, "MATCH_STATEMENT_LINE", "bankReconciliation#" + reconciliationId,
                statementLine.getNet().toPlainString() + " matched to "
                        + journalLine.getEntry().getEntryNo());
    }

    /** Works the two-sided proof out. Read-only; nothing it computes is stored. */
    @Transactional(readOnly = true)
    public Position position(BankReconciliation reconciliation) {
        List<BankStatementLine> statementLines = bankStatementLineRepository
                .findByReconciliationIdOrderByLineDateAscIdAsc(reconciliation.getId());
        Set<Long> matched = new HashSet<>();
        for (BankStatementLine line : statementLines) {
            if (line.getMatchedLineId() != null) {
                matched.add(line.getMatchedLineId());
            }
        }
        Set<Long> alreadyReconciled = new HashSet<>(bankStatementLineRepository
                .reconciledLineIds(reconciliation.getAccountId(), reconciliation.getId()));

        Account account = accountRepository.findById(reconciliation.getAccountId()).orElse(null);
        BigDecimal opening = account == null ? BigDecimal.ZERO : zero(account.getOpeningBalance());

        List<LedgerItem> unmatchedLedger = new ArrayList<>();
        BigDecimal bookBalance = opening;
        for (JournalLine line : journalLineRepository.postedByAccountUpTo(
                reconciliation.getAccountId(), reconciliation.getStatementDate())) {
            BigDecimal net = line.getDebitValue().subtract(line.getCreditValue());
            bookBalance = bookBalance.add(net);
            if (matched.contains(line.getId()) || alreadyReconciled.contains(line.getId())) {
                continue;
            }
            unmatchedLedger.add(new LedgerItem(line.getId(), line.getEntry().getEntryDate(),
                    line.getEntry().getEntryNo(), line.getEntry().getId(),
                    line.getMemo() != null ? line.getMemo() : line.getEntry().getMemo(), net));
        }

        List<BankStatementLine> unmatchedStatement = new ArrayList<>();
        for (BankStatementLine line : statementLines) {
            if (!line.isMatched()) {
                unmatchedStatement.add(line);
            }
        }

        BigDecimal inTransit = BigDecimal.ZERO;
        BigDecimal unpresented = BigDecimal.ZERO;
        for (LedgerItem item : unmatchedLedger) {
            if (item.net().signum() > 0) {
                inTransit = inTransit.add(item.net());
            } else {
                unpresented = unpresented.add(item.net().negate());
            }
        }

        BigDecimal notInBooks = BigDecimal.ZERO;
        for (BankStatementLine line : unmatchedStatement) {
            notInBooks = notInBooks.add(line.getNet());
        }

        BigDecimal adjustedBank = zero(reconciliation.getClosingBalance())
                .add(inTransit).subtract(unpresented);
        BigDecimal adjustedBook = bookBalance.add(notInBooks);

        return new Position(bookBalance, inTransit, unpresented, notInBooks,
                adjustedBank, adjustedBook, adjustedBank.subtract(adjustedBook),
                statementLines, unmatchedStatement, unmatchedLedger,
                available(reconciliation, matched, alreadyReconciled));
    }

    /** Ledger lines this statement could still be matched against. */
    private List<LedgerItem> available(BankReconciliation reconciliation,
                                       Set<Long> matched, Set<Long> alreadyReconciled) {
        List<LedgerItem> items = new ArrayList<>();
        for (JournalLine line : journalLineRepository.postedByAccountUpTo(
                reconciliation.getAccountId(), reconciliation.getStatementDate())) {
            if (matched.contains(line.getId()) || alreadyReconciled.contains(line.getId())) {
                continue;
            }
            items.add(new LedgerItem(line.getId(), line.getEntry().getEntryDate(),
                    line.getEntry().getEntryNo(), line.getEntry().getId(),
                    line.getMemo() != null ? line.getMemo() : line.getEntry().getMemo(),
                    line.getDebitValue().subtract(line.getCreditValue())));
        }
        return items;
    }

    @Transactional
    public BankReconciliation complete(Long id, String username) {
        BankReconciliation reconciliation = bankReconciliationRepository.findById(id).orElseThrow();
        if (reconciliation.isCompleted()) {
            return reconciliation;
        }
        if (!reconciliation.isEditable()) {
            throw new IllegalStateException("A void reconciliation cannot be completed");
        }
        if (!reconciliation.isStatementConsistent()) {
            throw new IllegalStateException("The statement does not agree with itself — opening "
                    + "plus its own lines comes to "
                    + zero(reconciliation.getOpeningBalance())
                        .add(reconciliation.getStatementMovement()).toPlainString()
                    + " against a closing balance of "
                    + zero(reconciliation.getClosingBalance()).toPlainString()
                    + ". Check the lines against the paper before going further.");
        }

        Position position = position(reconciliation);

        /*
         * Every statement line has to be matched, not merely explained. The two-sided proof
         * balances as soon as an unmatched line is carried into the book side as an adjustment —
         * that is what the adjustment is for — but signing off there would agree a statement while
         * the books are still missing the transaction, and the next statement would never raise it
         * again because this one had already accounted for it. The adjustment is a diagnostic
         * saying what the difference consists of, not a licence to complete.
         */
        if (position.hasUnmatchedStatement()) {
            throw new IllegalStateException(position.unmatchedStatement().size()
                    + " statement line" + (position.unmatchedStatement().size() == 1 ? "" : "s")
                    + " the books know nothing about — bank charges, interest, a direct debit "
                    + "nobody entered. Enter them as journal entries, then match them here. "
                    + "Agreeing the statement without them would leave the books short and this "
                    + "statement would never raise it again.");
        }
        if (position.difference().signum() != 0) {
            throw new IllegalStateException("This does not reconcile — the bank side comes to "
                    + position.adjustedBank().toPlainString() + " and the books to "
                    + position.adjustedBook().toPlainString() + ", a difference of "
                    + position.difference().toPlainString() + ".");
        }

        reconciliation.setStatus(ReconciliationStatus.COMPLETED);
        reconciliation.setCompletedBy(username);
        reconciliation.setCompletedAt(LocalDateTime.now());
        BankReconciliation saved = bankReconciliationRepository.save(reconciliation);
        auditService.log(MODULE, "COMPLETE_RECONCILIATION", "bankReconciliation#" + id,
                saved.getReference() + " reconciled at "
                        + zero(saved.getClosingBalance()).toPlainString());
        return saved;
    }

    @Transactional
    public BankReconciliation reopen(Long id) {
        BankReconciliation reconciliation = bankReconciliationRepository.findById(id).orElseThrow();
        if (reconciliation.isVoided()) {
            throw new IllegalStateException("A void reconciliation cannot be reopened");
        }
        reconciliation.setStatus(ReconciliationStatus.DRAFT);
        reconciliation.setCompletedBy(null);
        reconciliation.setCompletedAt(null);
        BankReconciliation saved = bankReconciliationRepository.save(reconciliation);
        auditService.log(MODULE, "REOPEN_RECONCILIATION", "bankReconciliation#" + id,
                saved.getReference());
        return saved;
    }

    /**
     * Voiding a reconciliation releases every ledger line it had agreed, so they come back on the
     * next statement. Nothing in the ledger moves, because nothing moved when it was completed.
     */
    @Transactional
    public BankReconciliation voidReconciliation(Long id, String reason) {
        BankReconciliation reconciliation = bankReconciliationRepository.findById(id).orElseThrow();
        reconciliation.setStatus(ReconciliationStatus.VOID);
        BankReconciliation saved = bankReconciliationRepository.save(reconciliation);
        auditService.log(MODULE, "VOID_RECONCILIATION", "bankReconciliation#" + id,
                saved.getReference() + (reason == null || reason.isBlank() ? "" : " — " + reason.trim()));
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        BankReconciliation reconciliation = bankReconciliationRepository.findById(id).orElse(null);
        if (reconciliation == null) {
            return;
        }
        if (!reconciliation.isEditable()) {
            throw new IllegalStateException("Only a draft reconciliation can be deleted. Void a "
                    + "completed one instead, which releases the entries it agreed.");
        }
        bankReconciliationRepository.delete(reconciliation);
        auditService.log(MODULE, "DELETE_RECONCILIATION", "bankReconciliation#" + id,
                reconciliation.getReference());
    }

    public BankReconciliationForm toForm(BankReconciliation r) {
        BankReconciliationForm form = new BankReconciliationForm();
        form.setAccountId(r.getAccountId());
        form.setStatementDate(r.getStatementDate());
        form.setOpeningBalance(r.getOpeningBalance());
        form.setClosingBalance(r.getClosingBalance());
        form.setNotes(r.getNotes());
        for (BankStatementLine line : r.getLines()) {
            BankReconciliationForm.Line row = new BankReconciliationForm.Line();
            row.setLineDate(line.getLineDate());
            row.setDescription(line.getDescription());
            row.setReference(line.getReference());
            row.setMoneyIn(line.getMoneyIn());
            row.setMoneyOut(line.getMoneyOut());
            form.getLines().add(row);
        }
        for (int i = 0; i < 5; i++) {
            form.getLines().add(new BankReconciliationForm.Line());
        }
        return form;
    }

    private String nextReference() {
        return "REC-" + LocalDate.now().getYear() + "-" + String.format("%05d", RANDOM.nextInt(100000));
    }

    private static ReconciliationStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ReconciliationStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
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

    public record LedgerItem(Long lineId, LocalDate date, String entryNo, Long entryId,
                             String memo, BigDecimal net) {

        public boolean isMoneyIn() {
            return net.signum() > 0;
        }

        public BigDecimal magnitude() {
            return net.abs();
        }
    }

    public record Position(BigDecimal bookBalance, BigDecimal inTransit, BigDecimal unpresented,
                           BigDecimal notInBooks, BigDecimal adjustedBank, BigDecimal adjustedBook,
                           BigDecimal difference, List<BankStatementLine> statementLines,
                           List<BankStatementLine> unmatchedStatement,
                           List<LedgerItem> unmatchedLedger, List<LedgerItem> available) {

        public boolean isReconciled() {
            return difference.signum() == 0;
        }

        public boolean hasUnmatchedStatement() {
            return !unmatchedStatement.isEmpty();
        }

        public boolean hasUnmatchedLedger() {
            return !unmatchedLedger.isEmpty();
        }
    }

    public record ReconciliationSummary(long all, long draft, long completed, long voided) {}
}
