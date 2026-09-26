/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : ExciseService.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : Excise duty on goods, and what is owed on it
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.ExciseDutyForm;
import com.ntaganira.heritier.ibook.entity.Account;
import com.ntaganira.heritier.ibook.entity.ExciseDuty;
import com.ntaganira.heritier.ibook.entity.Invoice;
import com.ntaganira.heritier.ibook.entity.InvoiceLine;
import com.ntaganira.heritier.ibook.entity.JournalEntry;
import com.ntaganira.heritier.ibook.entity.JournalLine;
import com.ntaganira.heritier.ibook.entity.NumberingSequence;
import com.ntaganira.heritier.ibook.entity.Product;
import com.ntaganira.heritier.ibook.enums.AccountType;
import com.ntaganira.heritier.ibook.enums.DocumentStatus;
import com.ntaganira.heritier.ibook.enums.ExciseBasis;
import com.ntaganira.heritier.ibook.enums.JournalEntryStatus;
import com.ntaganira.heritier.ibook.enums.JournalEntryType;
import com.ntaganira.heritier.ibook.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Excise duty: which goods carry it, at what rate, and what is owed on what has been sold.
 *
 * <p>Excise is charged on particular goods rather than on trade in general, so a duty is attached
 * to <strong>products</strong>. Two bases are supported because Rwandan excise uses both — a
 * percentage of value for some goods, a fixed amount per unit for others — and a system offering
 * only one would silently mis-charge the other half.
 *
 * <p><strong>Excise is not VAT and does not sit beside it.</strong> It forms part of the value that
 * VAT is then charged on, so on an invoice line the duty is added to the net first and VAT is
 * worked out on the sum. Charging both on the net would understate VAT; charging excise on the
 * VAT-inclusive figure would overstate the duty. That ordering is applied in
 * {@link InvoiceService} where the totals are worked out, and it is the reason this module had to
 * touch invoicing at all rather than merely reporting.
 *
 * <p>Revenue is unaffected: the duty is credited to a <strong>liability</strong>, never to income.
 * The company collects it from the customer on the state's behalf and it was never the company's
 * to earn.
 *
 * <p>Like the PAYE bands and the withholding rates, a duty ships <strong>unconfirmed</strong> and
 * an unconfirmed duty is never applied — it can be listed and edited but cannot reach an invoice.
 * Excise rates and the list of goods they cover change with each finance law.
 */
@Service
public class ExciseService {

    private static final String MODULE = "taxes";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PAYABLE_ACCOUNT_CODE = "2107";

    private final ExciseDutyRepository exciseDutyRepository;
    private final ProductRepository productRepository;
    private final InvoiceRepository invoiceRepository;
    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final JournalLineRepository journalLineRepository;
    private final NumberingSequenceRepository numberingSequenceRepository;
    private final AuditService auditService;

    public ExciseService(ExciseDutyRepository exciseDutyRepository,
                         ProductRepository productRepository,
                         InvoiceRepository invoiceRepository,
                         AccountRepository accountRepository,
                         JournalEntryRepository journalEntryRepository,
                         JournalLineRepository journalLineRepository,
                         NumberingSequenceRepository numberingSequenceRepository,
                         AuditService auditService) {
        this.exciseDutyRepository = exciseDutyRepository;
        this.productRepository = productRepository;
        this.invoiceRepository = invoiceRepository;
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.journalLineRepository = journalLineRepository;
        this.numberingSequenceRepository = numberingSequenceRepository;
        this.auditService = auditService;
    }

    // ----- Queries -----

    @Transactional(readOnly = true)
    public Page<ExciseDuty> list(String q, String active, Pageable pageable) {
        Boolean flag = active == null || active.isBlank() ? null
                : "true".equalsIgnoreCase(active);
        return exciseDutyRepository.search(trimToNull(q), flag, pageable);
    }

    @Transactional(readOnly = true)
    public ExciseDuty get(Long id) {
        return id == null ? null : exciseDutyRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<ExciseDuty> active() {
        return exciseDutyRepository.findByActiveTrueOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public List<Account> liabilityAccounts() {
        List<Account> accounts = new ArrayList<>();
        for (Account account : accountRepository.findByActiveTrueOrderByCodeAsc()) {
            if (account.getType() == AccountType.LIABILITY) {
                accounts.add(account);
            }
        }
        return accounts;
    }

    @Transactional(readOnly = true)
    public List<Account> paymentAccounts() {
        List<Account> accounts = new ArrayList<>();
        for (Account account : accountRepository.findByActiveTrueOrderByCodeAsc()) {
            String code = account.getCode();
            if (code != null && (code.startsWith("10") || code.startsWith("11"))) {
                accounts.add(account);
            }
        }
        return accounts;
    }

    /** Products carrying each duty, so the page can say what a rate change would affect. */
    @Transactional(readOnly = true)
    public long productCount(Long dutyId) {
        if (dutyId == null) {
            return 0;
        }
        return productRepository.findAll().stream()
                .filter(p -> dutyId.equals(p.getExciseDutyId()))
                .count();
    }

    @Transactional(readOnly = true)
    public List<Product> productsFor(Long dutyId) {
        if (dutyId == null) {
            return List.of();
        }
        return productRepository.findAll().stream()
                .filter(p -> dutyId.equals(p.getExciseDutyId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> missingExpectedAccounts() {
        List<String> missing = new ArrayList<>();
        if (accountRepository.findByCodeIgnoreCase(PAYABLE_ACCOUNT_CODE).isEmpty()) {
            missing.add(PAYABLE_ACCOUNT_CODE);
        }
        return missing;
    }

    @Transactional(readOnly = true)
    public ExciseSummary summary(LocalDate from, LocalDate to) {
        BigDecimal charged = BigDecimal.ZERO;
        Map<String, DutyTotal> byDuty = new LinkedHashMap<>();
        for (Invoice invoice : invoiceRepository.findAll()) {
            if (invoice.getStatus() == DocumentStatus.DRAFT
                    || invoice.getStatus() == DocumentStatus.VOID) {
                continue;
            }
            LocalDate date = invoice.getIssueDate();
            if (date == null || date.isBefore(from) || date.isAfter(to)) {
                continue;
            }
            for (InvoiceLine line : invoice.getLines()) {
                BigDecimal amount = zero(line.getExciseAmount());
                if (amount.signum() == 0) {
                    continue;
                }
                charged = charged.add(amount);
                String key = line.getExciseCode() == null ? "—" : line.getExciseCode();
                DutyTotal existing = byDuty.get(key);
                byDuty.put(key, existing == null
                        ? new DutyTotal(key, amount, 1)
                        : new DutyTotal(key, existing.amount().add(amount), existing.lines() + 1));
            }
        }

        // What is still held is read from the liability accounts themselves, so it is the balance
        // sheet's own figure rather than a second tally that could disagree with it.
        BigDecimal liability = BigDecimal.ZERO;
        BigDecimal remitted = BigDecimal.ZERO;
        // Each account is read once even where several duties share one, which they usually do —
        // reading per duty would count a shared account as many times as there are duties on it.
        java.util.Set<Long> accountsSeen = new java.util.LinkedHashSet<>();
        for (ExciseDuty duty : exciseDutyRepository.findAll()) {
            if (duty.getPayableAccountId() == null || !accountsSeen.add(duty.getPayableAccountId())) {
                continue;
            }
            for (JournalLine line : journalLineRepository
                    .postedByAccountUpTo(duty.getPayableAccountId(), to)) {
                liability = liability.add(line.getCreditValue()).subtract(line.getDebitValue());
                // Only a payment is money handed over. A reversal also debits this account, and
                // counting that as paid would say the duty had been settled when it was cancelled.
                boolean isPayment = line.getEntry().getType() == JournalEntryType.PAYMENT;
                if (isPayment && line.getDebitValue().signum() > 0
                        && !line.getEntry().getEntryDate().isBefore(from)) {
                    remitted = remitted.add(line.getDebitValue());
                }
            }
        }

        return new ExciseSummary(
                exciseDutyRepository.count(),
                exciseDutyRepository.countByActive(true),
                exciseDutyRepository.countByConfirmed(true),
                charged, remitted, liability, new ArrayList<>(byDuty.values()));
    }

    // ----- Editing -----

    @Transactional
    public ExciseDuty save(ExciseDutyForm form, Long id, String username) {
        String code = trimToNull(form.code());
        if (code == null) {
            throw new IllegalArgumentException("A duty needs a code");
        }
        boolean clash = id == null
                ? exciseDutyRepository.existsByCodeIgnoreCase(code)
                : exciseDutyRepository.existsByCodeIgnoreCaseAndIdNot(code, id);
        if (clash) {
            throw new IllegalArgumentException("Another duty already uses the code " + code);
        }

        ExciseBasis basis = parseBasis(form.basis());
        if (basis == ExciseBasis.PERCENT_OF_VALUE && form.rateValue().signum() <= 0) {
            throw new IllegalArgumentException("A duty charged on value needs a rate above zero");
        }
        if (basis == ExciseBasis.AMOUNT_PER_UNIT && form.amountPerUnitValue().signum() <= 0) {
            throw new IllegalArgumentException("A duty charged per unit needs an amount above zero");
        }
        if (form.payableAccountId() == null) {
            throw new IllegalArgumentException("A duty needs the liability account it is held in — "
                    + "it is collected for the state and was never the company's to earn");
        }
        Account payable = accountRepository.findById(form.payableAccountId())
                .orElseThrow(() -> new IllegalArgumentException("That account no longer exists"));
        if (payable.getType() != AccountType.LIABILITY) {
            throw new IllegalArgumentException(payable.getCode() + " is not a liability. Excise is "
                    + "collected on the state's behalf, so crediting it to income would report "
                    + "somebody else's money as earnings.");
        }

        ExciseDuty duty = id == null ? new ExciseDuty()
                : exciseDutyRepository.findById(id).orElseThrow();

        boolean figuresChanged = id == null
                || basis != duty.getBasis()
                || zero(duty.getRate()).compareTo(form.rateValue()) != 0
                || zero(duty.getAmountPerUnit()).compareTo(form.amountPerUnitValue()) != 0;

        duty.setCode(code);
        duty.setName(trimToNull(form.name()));
        duty.setBasis(basis);
        duty.setRate(basis == ExciseBasis.PERCENT_OF_VALUE ? form.rateValue() : BigDecimal.ZERO);
        duty.setAmountPerUnit(basis == ExciseBasis.AMOUNT_PER_UNIT
                ? form.amountPerUnitValue() : BigDecimal.ZERO);
        duty.setUnitLabel(trimToNull(form.unitLabel()));
        duty.setAppliesTo(trimToNull(form.appliesTo()));
        duty.setPayableAccountId(payable.getId());
        duty.setPayableAccountCode(payable.getCode());
        duty.setPayableAccountName(payable.getName());
        duty.setRateSource(trimToNull(form.rateSource()));
        duty.setActive(form.activeValue());

        // Approval attaches to the figures. Renaming a duty leaves it confirmed; changing what it
        // charges does not.
        if (figuresChanged) {
            duty.setConfirmed(false);
            duty.setConfirmedBy(null);
            duty.setConfirmedAt(null);
        }

        ExciseDuty saved = exciseDutyRepository.save(duty);
        auditService.log(MODULE, id == null ? "CREATE_EXCISE" : "UPDATE_EXCISE",
                "exciseDuty#" + saved.getId(), saved.getCode() + " — " + saved.getName());
        return saved;
    }

    @Transactional
    public ExciseDuty confirm(Long id, String source, String username) {
        ExciseDuty duty = exciseDutyRepository.findById(id).orElseThrow();
        if (duty.getPayableAccountId() == null) {
            throw new IllegalStateException("Choose the liability account this duty is held in first");
        }
        duty.setConfirmed(true);
        duty.setConfirmedBy(username);
        duty.setConfirmedAt(LocalDateTime.now());
        if (trimToNull(source) != null) {
            duty.setRateSource(source.trim());
        }
        ExciseDuty saved = exciseDutyRepository.save(duty);
        auditService.log(MODULE, "CONFIRM_EXCISE", "exciseDuty#" + id,
                saved.getCode() + " confirmed by " + username);
        return saved;
    }

    @Transactional
    public ExciseDuty withdraw(Long id, String username) {
        ExciseDuty duty = exciseDutyRepository.findById(id).orElseThrow();
        duty.setConfirmed(false);
        duty.setConfirmedBy(null);
        duty.setConfirmedAt(null);
        ExciseDuty saved = exciseDutyRepository.save(duty);
        auditService.log(MODULE, "WITHDRAW_EXCISE", "exciseDuty#" + id,
                saved.getCode() + " confirmation withdrawn by " + username);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        ExciseDuty duty = exciseDutyRepository.findById(id).orElse(null);
        if (duty == null) {
            return;
        }
        long carried = productCount(id);
        if (carried > 0) {
            throw new IllegalStateException("This duty is on " + carried
                    + (carried == 1 ? " product" : " products")
                    + ". Deactivate it instead — deleting it would leave invoices that charged it "
                    + "unable to say what they charged.");
        }
        exciseDutyRepository.delete(duty);
        auditService.log(MODULE, "DELETE_EXCISE", "exciseDuty#" + id,
                duty.getCode() + " — " + duty.getName());
    }

    /** Attaches a duty to a product, or takes it off when the duty is null. */
    @Transactional
    public void assignToProduct(Long productId, Long dutyId) {
        Product product = productRepository.findById(productId).orElseThrow();
        if (dutyId != null && exciseDutyRepository.findById(dutyId).isEmpty()) {
            throw new IllegalArgumentException("That duty could not be found");
        }
        product.setExciseDutyId(dutyId);
        productRepository.save(product);
        auditService.log(MODULE, dutyId == null ? "UNASSIGN_EXCISE" : "ASSIGN_EXCISE",
                "product#" + productId, product.getName());
    }

    /** Hands the duty over: Dr the liability, Cr the account the money left. */
    @Transactional
    public JournalEntry remit(Long dutyId, LocalDate paymentDate, BigDecimal amount,
                              Long paymentAccountId, String declarationNo, String username) {
        ExciseDuty duty = exciseDutyRepository.findById(dutyId)
                .orElseThrow(() -> new IllegalArgumentException("Choose the duty being paid over"));
        if (duty.getPayableAccountId() == null) {
            throw new IllegalStateException("That duty has no liability account");
        }
        BigDecimal value = zero(amount);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("A payment has to be for more than nothing");
        }
        Account payable = accountRepository.findById(duty.getPayableAccountId())
                .orElseThrow(() -> new IllegalStateException("The liability account no longer exists"));
        Account source = accountRepository.findById(paymentAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Choose the account the money left"));

        JournalEntry entry = JournalEntry.builder()
                .entryNo(nextJournalNo())
                .entryDate(paymentDate == null ? LocalDate.now() : paymentDate)
                .type(JournalEntryType.PAYMENT)
                .status(JournalEntryStatus.POSTED)
                .reference(declarationNo == null || declarationNo.isBlank()
                        ? "Excise remittance" : declarationNo.trim())
                .memo("Excise duty remitted — " + duty.getCode())
                .createdBy(username)
                .build();
        entry.addLine(JournalLine.builder()
                .accountId(payable.getId())
                .accountCode(payable.getCode())
                .accountName(payable.getName())
                .memo(duty.getCode())
                .debit(value)
                .credit(BigDecimal.ZERO)
                .sortOrder(0)
                .build());
        entry.addLine(JournalLine.builder()
                .accountId(source.getId())
                .accountCode(source.getCode())
                .accountName(source.getName())
                .memo("Excise duty")
                .debit(BigDecimal.ZERO)
                .credit(value)
                .sortOrder(1)
                .build());
        entry.setTotalDebits(value);
        entry.setTotalCredits(value);
        JournalEntry saved = journalEntryRepository.save(entry);
        auditService.log(MODULE, "REMIT_EXCISE", "journalEntry#" + saved.getId(),
                value.toPlainString() + " remitted as " + saved.getEntryNo());
        return saved;
    }

    public ExciseDutyForm toForm(ExciseDuty d) {
        return new ExciseDutyForm(d.getCode(), d.getName(), d.getBasis().name(), d.getRate(),
                d.getAmountPerUnit(), d.getUnitLabel(), d.getAppliesTo(), d.getPayableAccountId(),
                d.getRateSource(), d.isActive());
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

    private static ExciseBasis parseBasis(String basis) {
        if (basis == null || basis.isBlank()) {
            return ExciseBasis.PERCENT_OF_VALUE;
        }
        try {
            return ExciseBasis.valueOf(basis.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ExciseBasis.PERCENT_OF_VALUE;
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

    public record DutyTotal(String code, BigDecimal amount, int lines) {}

    public record ExciseSummary(long all, long active, long confirmed, BigDecimal chargedInPeriod,
                                BigDecimal remittedInPeriod, BigDecimal liability,
                                List<DutyTotal> byDuty) {}
}
