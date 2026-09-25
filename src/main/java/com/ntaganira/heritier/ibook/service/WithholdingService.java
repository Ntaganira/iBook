/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : WithholdingService.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : Tax withheld from suppliers, and handing it over
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.BillPaymentForm;
import com.ntaganira.heritier.ibook.dto.WithholdingCertificateForm;
import com.ntaganira.heritier.ibook.dto.WithholdingSettingsForm;
import com.ntaganira.heritier.ibook.entity.*;
import com.ntaganira.heritier.ibook.enums.JournalEntryStatus;
import com.ntaganira.heritier.ibook.enums.JournalEntryType;
import com.ntaganira.heritier.ibook.enums.WithholdingStatus;
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
 * Tax withheld from what suppliers are paid, and the handing over of it.
 *
 * <p><strong>Withholding is not a cost.</strong> The whole of the bill is still the company's
 * expense; part of it is simply paid to the RRA rather than to the supplier. So a certificate posts
 * <em>Dr accounts payable / Cr withholding payable</em> and touches no expense account — it moves a
 * debt from one creditor to another.
 *
 * <p>It is recorded through {@link BillService#recordPayment}, the ordinary bill-payment path,
 * because that is genuinely what it is: a part-settlement of the bill in which the money goes to the
 * state. Doing it that way keeps the bill's own balance, the aging report and the payables total all
 * saying the same thing. Posting a separate entry beside the bill instead would leave the ledger
 * showing 103,000 owed while aging still showed 118,000, and nothing would reconcile the two.
 *
 * <p>Nothing can be withheld until the rates are confirmed, for the same reason payroll cannot run:
 * withholding takes money off somebody's payment and owes it to the state, and a wrong rate shorts
 * either the supplier or the RRA. The rates shipped here are a starting point, not an authority.
 *
 * <p>What has been handed over is read from the <strong>ledger</strong> — movements on the
 * withholding payable account — rather than from a record of its own. The liability is the truth
 * about what is owed, and a second tally beside it would only be something to disagree with.
 */
@Service
public class WithholdingService {

    private static final String MODULE = "taxes";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PAYABLE_ACCOUNT_CODE = "2106";

    private final WithholdingSettingsRepository withholdingSettingsRepository;
    private final WithholdingCertificateRepository withholdingCertificateRepository;
    private final BillRepository billRepository;
    private final VendorRepository vendorRepository;
    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final JournalLineRepository journalLineRepository;
    private final NumberingSequenceRepository numberingSequenceRepository;
    private final BillService billService;
    private final AuditService auditService;

    public WithholdingService(WithholdingSettingsRepository withholdingSettingsRepository,
                              WithholdingCertificateRepository withholdingCertificateRepository,
                              BillRepository billRepository,
                              VendorRepository vendorRepository,
                              AccountRepository accountRepository,
                              JournalEntryRepository journalEntryRepository,
                              JournalLineRepository journalLineRepository,
                              NumberingSequenceRepository numberingSequenceRepository,
                              BillService billService,
                              AuditService auditService) {
        this.withholdingSettingsRepository = withholdingSettingsRepository;
        this.withholdingCertificateRepository = withholdingCertificateRepository;
        this.billRepository = billRepository;
        this.vendorRepository = vendorRepository;
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.journalLineRepository = journalLineRepository;
        this.numberingSequenceRepository = numberingSequenceRepository;
        this.billService = billService;
        this.auditService = auditService;
    }

    // ----- Settings -----

    @Transactional
    public WithholdingSettings settings() {
        return withholdingSettingsRepository.findFirstByOrderByIdAsc()
                .orElseGet(this::createStartingPoint);
    }

    /**
     * The rates a fresh installation starts with, every one of them unconfirmed.
     *
     * <p>These are typed from published Rwandan withholding rates and are <strong>not</strong> an
     * authority on them. Which rate applies to a particular payment is a judgement about the nature
     * of the supply, so the table carries a description and nothing here chooses for the user.
     */
    private WithholdingSettings createStartingPoint() {
        WithholdingSettings settings = WithholdingSettings.builder()
                .confirmed(false)
                .payableAccountId(accountRepository.findByCodeIgnoreCase(PAYABLE_ACCOUNT_CODE)
                        .map(Account::getId).orElse(null))
                .build();
        settings.addRate(rate(0, "WHT15", "Services and fees", "15.00",
                "Professional, technical and management fees, and payments to non-residents."));
        settings.addRate(rate(1, "WHT5", "Imported goods", "5.00",
                "Withheld on the customs value of imports where it applies."));
        settings.addRate(rate(2, "WHT3", "Public tenders", "3.00",
                "Payments under a public tender to a supplier without a tax clearance certificate."));
        return withholdingSettingsRepository.save(settings);
    }

    private static WithholdingRate rate(int order, String code, String name,
                                        String percent, String appliesTo) {
        return WithholdingRate.builder()
                .sortOrder(order)
                .code(code)
                .name(name)
                .rate(new BigDecimal(percent))
                .appliesTo(appliesTo)
                .build();
    }

    @Transactional
    public WithholdingSettings saveSettings(WithholdingSettingsForm form) {
        WithholdingSettings settings = settings();
        List<WithholdingSettingsForm.Row> rows = form.filledRates();
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("A withholding table needs at least one rate");
        }
        for (WithholdingSettingsForm.Row row : rows) {
            if (row.rateValue().signum() < 0
                    || row.rateValue().compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException("A withholding rate has to be between 0 and 100");
            }
        }

        settings.setPayableAccountId(form.getPayableAccountId());
        settings.setRateSource(trimToNull(form.getRateSource()));
        settings.getRates().clear();
        int order = 0;
        for (WithholdingSettingsForm.Row row : rows) {
            settings.addRate(WithholdingRate.builder()
                    .code(trimToNull(row.getCode()))
                    .name(trimToNull(row.getName()))
                    .rate(row.rateValue())
                    .appliesTo(trimToNull(row.getAppliesTo()))
                    .sortOrder(order++)
                    .build());
        }

        // Approval attaches to figures, not to a screen, so changing any of them withdraws it.
        settings.setConfirmed(false);
        settings.setConfirmedBy(null);
        settings.setConfirmedAt(null);

        WithholdingSettings saved = withholdingSettingsRepository.save(settings);
        auditService.log(MODULE, "UPDATE_WHT_RATES", "withholdingSettings#" + saved.getId(),
                "Rates edited — confirmation withdrawn");
        return saved;
    }

    @Transactional
    public WithholdingSettings confirm(String source, String username) {
        WithholdingSettings settings = settings();
        if (settings.getPayableAccountId() == null) {
            throw new IllegalStateException("Choose the liability account withheld money is held in "
                    + "before confirming these rates");
        }
        if (settings.getRates().isEmpty()) {
            throw new IllegalStateException("A table with no rates cannot be confirmed");
        }
        settings.setConfirmed(true);
        settings.setConfirmedBy(username);
        settings.setConfirmedAt(LocalDateTime.now());
        if (trimToNull(source) != null) {
            settings.setRateSource(source.trim());
        }
        WithholdingSettings saved = withholdingSettingsRepository.save(settings);
        auditService.log(MODULE, "CONFIRM_WHT_RATES", "withholdingSettings#" + saved.getId(),
                "Confirmed by " + username
                        + (saved.getRateSource() == null ? "" : " against " + saved.getRateSource()));
        return saved;
    }

    @Transactional
    public WithholdingSettings withdrawConfirmation(String username) {
        WithholdingSettings settings = settings();
        settings.setConfirmed(false);
        settings.setConfirmedBy(null);
        settings.setConfirmedAt(null);
        WithholdingSettings saved = withholdingSettingsRepository.save(settings);
        auditService.log(MODULE, "WITHDRAW_WHT_RATES", "withholdingSettings#" + saved.getId(),
                "Confirmation withdrawn by " + username);
        return saved;
    }

    public WithholdingSettingsForm toForm(WithholdingSettings settings) {
        WithholdingSettingsForm form = new WithholdingSettingsForm();
        form.setPayableAccountId(settings.getPayableAccountId());
        form.setRateSource(settings.getRateSource());
        for (WithholdingRate rate : settings.getRates()) {
            WithholdingSettingsForm.Row row = new WithholdingSettingsForm.Row();
            row.setCode(rate.getCode());
            row.setName(rate.getName());
            row.setRate(rate.getRate());
            row.setAppliesTo(rate.getAppliesTo());
            form.getRates().add(row);
        }
        for (int i = 0; i < 3; i++) {
            form.getRates().add(new WithholdingSettingsForm.Row());
        }
        return form;
    }

    // ----- Queries -----

    @Transactional(readOnly = true)
    public Page<WithholdingCertificate> list(String q, Long vendorId, String status,
                                             Pageable pageable) {
        return withholdingCertificateRepository.search(trimToNull(q), vendorId,
                parseStatus(status), pageable);
    }

    @Transactional(readOnly = true)
    public WithholdingCertificate get(Long id) {
        return id == null ? null : withholdingCertificateRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public JournalEntry journalFor(WithholdingCertificate certificate) {
        if (certificate == null || certificate.getJournalEntryId() == null) {
            return null;
        }
        return journalEntryRepository.findById(certificate.getJournalEntryId()).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<WithholdingRate> rates() {
        return settingsReadOnly() == null ? List.of() : settingsReadOnly().getRates();
    }

    @Transactional(readOnly = true)
    public WithholdingSettings settingsReadOnly() {
        return withholdingSettingsRepository.findFirstByOrderByIdAsc().orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Vendor> vendors() {
        return vendorRepository.findAll();
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

    @Transactional(readOnly = true)
    public List<Account> liabilityAccounts() {
        return accountRepository.findByActiveTrueOrderByCodeAsc();
    }

    /**
     * Posted bills that still owe the supplier something and have not been withheld from. Offering
     * a bill that would then be refused reads as a fault rather than as the rule it is.
     */
    @Transactional(readOnly = true)
    public List<BillChoice> withholdableBills() {
        List<BillChoice> choices = new ArrayList<>();
        for (Bill bill : billRepository.findAll()) {
            if (!bill.isPosted() || bill.getBalanceDue().signum() <= 0) {
                continue;
            }
            if (!withholdingCertificateRepository.liveForBill(bill.getId()).isEmpty()) {
                continue;
            }
            choices.add(new BillChoice(bill.getId(), bill.getBillNo(), bill.getVendorName(),
                    zero(bill.getSubtotal()), bill.getBalanceDue()));
        }
        return choices;
    }

    @Transactional(readOnly = true)
    public WithholdingSummary summary(LocalDate from, LocalDate to) {
        WithholdingSettings settings = settingsReadOnly();
        BigDecimal withheld = zero(withholdingCertificateRepository.withheldBetween(from, to));
        BigDecimal remitted = BigDecimal.ZERO;
        BigDecimal liability = BigDecimal.ZERO;
        if (settings != null && settings.getPayableAccountId() != null) {
            /*
             * Read from the ledger. The running balance is what is still held, and that figure is
             * the one to act on.
             *
             * "Handed over" counts only debits on payment entries. Not every debit to this account
             * is a remittance — voiding a certificate also debits it, and counting that as money
             * paid to the RRA would say the liability had been settled when it had merely been
             * cancelled. A debit made by some other route is left out rather than assumed, which
             * understates what has been paid; that is the safe direction, and the liability itself
             * is unaffected either way.
             */
            for (JournalLine line : journalLineRepository
                    .postedByAccountUpTo(settings.getPayableAccountId(), to)) {
                liability = liability.add(line.getCreditValue()).subtract(line.getDebitValue());
                LocalDate date = line.getEntry().getEntryDate();
                boolean isPayment = line.getEntry().getType() == JournalEntryType.PAYMENT;
                if (isPayment && !date.isBefore(from) && line.getDebitValue().signum() > 0) {
                    remitted = remitted.add(line.getDebitValue());
                }
            }
        }
        return new WithholdingSummary(
                withholdingCertificateRepository.count(),
                withholdingCertificateRepository.countByStatus(WithholdingStatus.DRAFT),
                withholdingCertificateRepository.countByStatus(WithholdingStatus.ISSUED),
                withholdingCertificateRepository.countByStatus(WithholdingStatus.VOID),
                withheld, remitted, liability,
                settings != null && settings.isReadyToWithhold());
    }

    @Transactional(readOnly = true)
    public List<WithholdingCertificate> issuedBetween(LocalDate from, LocalDate to) {
        return withholdingCertificateRepository.issuedBetween(from, to);
    }

    /** What was withheld from each supplier in a period, which is what a return is made up of. */
    @Transactional(readOnly = true)
    public List<VendorTotal> byVendor(LocalDate from, LocalDate to) {
        Map<String, VendorTotal> totals = new LinkedHashMap<>();
        for (WithholdingCertificate c : withholdingCertificateRepository.issuedBetween(from, to)) {
            String key = c.getVendorName() == null ? "—" : c.getVendorName();
            VendorTotal existing = totals.get(key);
            if (existing == null) {
                totals.put(key, new VendorTotal(key, c.getVendorTaxId(), zero(c.getBaseAmount()),
                        zero(c.getAmount()), 1));
            } else {
                totals.put(key, new VendorTotal(key,
                        existing.taxId() == null ? c.getVendorTaxId() : existing.taxId(),
                        existing.base().add(zero(c.getBaseAmount())),
                        existing.withheld().add(zero(c.getAmount())),
                        existing.count() + 1));
            }
        }
        return new ArrayList<>(totals.values());
    }

    // ----- Certificates -----

    @Transactional
    public WithholdingCertificate save(WithholdingCertificateForm form, Long id, String username) {
        WithholdingSettings settings = settings();
        if (!settings.isConfirmed()) {
            throw new IllegalStateException("The withholding rates have not been confirmed. Check "
                    + "them against the current RRA schedule at Withholding tax and confirm them "
                    + "there — nothing is withheld until somebody has.");
        }

        Bill bill = billRepository.findById(form.billId())
                .orElseThrow(() -> new IllegalArgumentException("That bill could not be found"));
        if (!bill.isPosted()) {
            throw new IllegalStateException("Post the bill before withholding from it — there is "
                    + "nothing owed to the supplier until it is in the books");
        }
        WithholdingRate rate = settings.getRates().stream()
                .filter(r -> r.getId().equals(form.rateId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("That rate is not in the table"));

        for (WithholdingCertificate other : withholdingCertificateRepository.liveForBill(bill.getId())) {
            if (id == null || !other.getId().equals(id)) {
                throw new IllegalStateException(bill.getBillNo() + " already has certificate "
                        + other.getCertificateNo() + ". Withholding it twice would take the money "
                        + "off the supplier once and owe it to the RRA twice.");
            }
        }

        BigDecimal base = form.baseValue();
        if (base.signum() <= 0) {
            throw new IllegalArgumentException("There is nothing to withhold from");
        }
        if (base.compareTo(zero(bill.getTotal())) > 0) {
            throw new IllegalArgumentException("The base cannot exceed the bill — "
                    + bill.getBillNo() + " is for " + zero(bill.getTotal()).toPlainString());
        }
        BigDecimal amount = rate.on(base);
        if (amount.compareTo(bill.getBalanceDue()) > 0) {
            throw new IllegalArgumentException("Withholding " + amount.toPlainString()
                    + " is more than the " + bill.getBalanceDue().toPlainString()
                    + " still owed on this bill. Money already paid to the supplier cannot be "
                    + "withheld after the fact.");
        }

        Vendor vendor = bill.getVendorId() == null ? null
                : vendorRepository.findById(bill.getVendorId()).orElse(null);

        WithholdingCertificate certificate;
        if (id == null) {
            certificate = new WithholdingCertificate();
            certificate.setCertificateNo(nextCertificateNo());
            certificate.setCreatedBy(username);
            certificate.setStatus(WithholdingStatus.DRAFT);
        } else {
            certificate = withholdingCertificateRepository.findById(id).orElseThrow();
            if (!certificate.isEditable()) {
                throw new IllegalStateException("An issued certificate cannot be changed — the "
                        + "supplier has been given it. Void it and raise another.");
            }
        }

        certificate.setBillId(bill.getId());
        certificate.setBillNo(bill.getBillNo());
        certificate.setVendorId(bill.getVendorId());
        certificate.setVendorName(bill.getVendorName());
        certificate.setVendorTaxId(vendor == null ? null : vendor.getTaxId());
        certificate.setCertificateDate(form.certificateDate());
        certificate.setRateCode(rate.getCode());
        certificate.setRateName(rate.getName());
        certificate.setRate(rate.getRate());
        certificate.setBaseAmount(base);
        certificate.setAmount(amount);
        certificate.setNotes(trimToNull(form.notes()));

        WithholdingCertificate saved = withholdingCertificateRepository.save(certificate);
        auditService.log(MODULE, id == null ? "CREATE_WHT_CERTIFICATE" : "UPDATE_WHT_CERTIFICATE",
                "withholdingCertificate#" + saved.getId(),
                saved.getCertificateNo() + " — " + saved.getVendorName()
                        + " " + saved.getAmount().toPlainString());

        if (form.issueNowValue()) {
            saved = issue(saved.getId(), username);
        }
        return saved;
    }

    /**
     * Issues the certificate: the withheld amount is recorded against the bill as a payment whose
     * destination is the withholding liability rather than a bank account.
     */
    @Transactional
    public WithholdingCertificate issue(Long id, String username) {
        WithholdingCertificate certificate = withholdingCertificateRepository.findById(id).orElseThrow();
        if (certificate.isIssued()) {
            return certificate;
        }
        if (certificate.isVoided()) {
            throw new IllegalStateException("A void certificate cannot be issued");
        }
        WithholdingSettings settings = settings();
        if (!settings.isConfirmed()) {
            throw new IllegalStateException("The withholding rates have not been confirmed");
        }
        Account payable = accountRepository.findById(settings.getPayableAccountId())
                .orElseThrow(() -> new IllegalStateException("The withholding payable account no "
                        + "longer exists — choose one at Withholding tax"));

        BillPayment payment = billService.recordPayment(certificate.getBillId(),
                new BillPaymentForm(certificate.getCertificateDate(), certificate.getAmount(),
                        "BANK_TRANSFER", certificate.getCertificateNo(), payable.getId(),
                        "Tax withheld — " + certificate.getCertificateNo()));

        certificate.setBillPaymentId(payment.getId());
        certificate.setJournalEntryId(payment.getJournalEntryId());
        certificate.setStatus(WithholdingStatus.ISSUED);
        WithholdingCertificate saved = withholdingCertificateRepository.save(certificate);
        auditService.log(MODULE, "ISSUE_WHT_CERTIFICATE", "withholdingCertificate#" + id,
                saved.getCertificateNo() + " issued — " + saved.getAmount().toPlainString()
                        + " withheld from " + saved.getVendorName());
        return saved;
    }

    /**
     * Voiding writes a reversing entry, which puts the debt back on the supplier and takes it off
     * the RRA. The bill payment is left in place and countered rather than deleted, because a
     * payment that was recorded is a fact about what the books said.
     */
    @Transactional
    public WithholdingCertificate voidCertificate(Long id, String reason, String username) {
        WithholdingCertificate certificate = withholdingCertificateRepository.findById(id).orElseThrow();
        if (certificate.isVoided()) {
            return certificate;
        }
        if (certificate.isIssued() && certificate.getJournalEntryId() != null) {
            JournalEntry original = journalEntryRepository
                    .findById(certificate.getJournalEntryId()).orElse(null);
            if (original != null) {
                JournalEntry reversal = JournalEntry.builder()
                        .entryNo(nextJournalNo())
                        .entryDate(LocalDate.now())
                        .type(JournalEntryType.ADJUSTMENT)
                        .status(JournalEntryStatus.POSTED)
                        .reference(certificate.getCertificateNo())
                        .memo("Reversal of " + original.getEntryNo() + " — voided withholding "
                                + certificate.getCertificateNo())
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
                reversal.setTotalDebits(sumDebits(reversal));
                reversal.setTotalCredits(sumCredits(reversal));
                journalEntryRepository.save(reversal);
            }
        }
        certificate.setStatus(WithholdingStatus.VOID);
        WithholdingCertificate saved = withholdingCertificateRepository.save(certificate);
        auditService.log(MODULE, "VOID_WHT_CERTIFICATE", "withholdingCertificate#" + id,
                saved.getCertificateNo()
                        + (reason == null || reason.isBlank() ? "" : " — " + reason.trim()));
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        WithholdingCertificate certificate = withholdingCertificateRepository.findById(id).orElse(null);
        if (certificate == null) {
            return;
        }
        if (!certificate.isEditable()) {
            throw new IllegalStateException("Only a draft certificate can be deleted. Void an "
                    + "issued one instead — the supplier has already been given it.");
        }
        withholdingCertificateRepository.delete(certificate);
        auditService.log(MODULE, "DELETE_WHT_CERTIFICATE", "withholdingCertificate#" + id,
                certificate.getCertificateNo() + " draft deleted");
    }

    /** Hands the withheld money over: Dr the liability, Cr the account the money left. */
    @Transactional
    public JournalEntry remit(LocalDate paymentDate, BigDecimal amount, Long paymentAccountId,
                              String declarationNo, String username) {
        WithholdingSettings settings = settings();
        if (settings.getPayableAccountId() == null) {
            throw new IllegalStateException("No withholding payable account has been chosen");
        }
        BigDecimal value = zero(amount);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("A remittance has to be for more than nothing");
        }
        Account payable = accountRepository.findById(settings.getPayableAccountId())
                .orElseThrow(() -> new IllegalStateException("The withholding payable account no "
                        + "longer exists"));
        Account source = accountRepository.findById(paymentAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Choose the account the money left"));

        JournalEntry entry = JournalEntry.builder()
                .entryNo(nextJournalNo())
                .entryDate(paymentDate == null ? LocalDate.now() : paymentDate)
                .type(JournalEntryType.PAYMENT)
                .status(JournalEntryStatus.POSTED)
                .reference(declarationNo == null || declarationNo.isBlank()
                        ? "WHT remittance" : declarationNo.trim())
                .memo("Withholding tax remitted to the RRA")
                .createdBy(username)
                .build();
        entry.addLine(JournalLine.builder()
                .accountId(payable.getId())
                .accountCode(payable.getCode())
                .accountName(payable.getName())
                .memo(declarationNo == null || declarationNo.isBlank() ? "WHT" : declarationNo.trim())
                .debit(value)
                .credit(BigDecimal.ZERO)
                .sortOrder(0)
                .build());
        entry.addLine(JournalLine.builder()
                .accountId(source.getId())
                .accountCode(source.getCode())
                .accountName(source.getName())
                .memo("Withholding tax")
                .debit(BigDecimal.ZERO)
                .credit(value)
                .sortOrder(1)
                .build());
        entry.setTotalDebits(value);
        entry.setTotalCredits(value);
        JournalEntry saved = journalEntryRepository.save(entry);
        auditService.log(MODULE, "REMIT_WHT", "journalEntry#" + saved.getId(),
                value.toPlainString() + " remitted as " + saved.getEntryNo());
        return saved;
    }

    public WithholdingCertificateForm toForm(WithholdingCertificate c) {
        return new WithholdingCertificateForm(c.getBillId(), rateIdFor(c), c.getCertificateDate(),
                c.getBaseAmount(), c.getNotes(), Boolean.FALSE);
    }

    private Long rateIdFor(WithholdingCertificate certificate) {
        WithholdingSettings settings = settingsReadOnly();
        if (settings == null || certificate.getRateCode() == null) {
            return null;
        }
        return settings.getRates().stream()
                .filter(r -> certificate.getRateCode().equalsIgnoreCase(r.getCode()))
                .map(WithholdingRate::getId)
                .findFirst()
                .orElse(null);
    }

    /** Codes this module expects but the chart may not have, so the page can say which are missing. */
    @Transactional(readOnly = true)
    public List<String> missingExpectedAccounts() {
        List<String> missing = new ArrayList<>();
        if (accountRepository.findByCodeIgnoreCase(PAYABLE_ACCOUNT_CODE).isEmpty()) {
            missing.add(PAYABLE_ACCOUNT_CODE);
        }
        return missing;
    }

    private String nextCertificateNo() {
        NumberingSequence seq = numberingSequenceRepository.findByDocType("WITHHOLDING").orElse(null);
        if (seq != null) {
            long next = seq.getNextNumber();
            String prefix = seq.getPrefix() == null ? "WHT-" : seq.getPrefix();
            String suffix = seq.getSuffix() == null ? "" : seq.getSuffix();
            int padding = seq.getPadding() == 0 ? 4 : seq.getPadding();
            String number = prefix + String.format("%0" + padding + "d", next) + suffix;
            seq.setNextNumber(next + 1);
            numberingSequenceRepository.save(seq);
            return number;
        }
        return "WHT-" + LocalDate.now().getYear() + "-" + String.format("%05d", RANDOM.nextInt(100000));
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

    private static WithholdingStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return WithholdingStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
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

    public record BillChoice(Long id, String billNo, String vendorName,
                             BigDecimal netOfVat, BigDecimal balanceDue) {}

    public record VendorTotal(String vendorName, String taxId, BigDecimal base,
                              BigDecimal withheld, int count) {}

    public record WithholdingSummary(long all, long draft, long issued, long voided,
                                     BigDecimal withheldInPeriod, BigDecimal remittedInPeriod,
                                     BigDecimal liability, boolean ready) {

        public BigDecimal outstanding() {
            return liability;
        }
    }
}
