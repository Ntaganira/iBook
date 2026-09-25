/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : PayrollSettingsService.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : The rates and accounts payroll is worked out from, and the gate on using them
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.PayrollSettingsForm;
import com.ntaganira.heritier.ibook.entity.Account;
import com.ntaganira.heritier.ibook.entity.PayeBand;
import com.ntaganira.heritier.ibook.entity.PayrollSettings;
import com.ntaganira.heritier.ibook.repository.AccountRepository;
import com.ntaganira.heritier.ibook.repository.PayrollSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Holds the one set of rates and accounts every payroll run reads, and the confirmation gate that
 * decides whether a run may post at all.
 *
 * <p>The figures created on first use are a <strong>starting point, not an authority</strong>.
 * They are typed from published Rwandan rates and they may already be out of date by the time
 * anybody reads this: PAYE bands and RSSB rates both move, and a system that treated a developer's
 * recollection as the tax code would produce confident wrong numbers for a year before the RRA
 * mentioned it.
 *
 * <p>So they ship unconfirmed and nothing posts until somebody says the words. Confirming records
 * who and when, and what they checked against. Any later edit to a rate clears the confirmation,
 * because approval attaches to the figures that were approved and not to the screen they sit on.
 */
@Service
public class PayrollSettingsService {

    private static final String MODULE = "payroll";

    private final PayrollSettingsRepository payrollSettingsRepository;
    private final AccountRepository accountRepository;
    private final AuditService auditService;

    public PayrollSettingsService(PayrollSettingsRepository payrollSettingsRepository,
                                  AccountRepository accountRepository,
                                  AuditService auditService) {
        this.payrollSettingsRepository = payrollSettingsRepository;
        this.accountRepository = accountRepository;
        this.auditService = auditService;
    }

    @Transactional
    public PayrollSettings current() {
        return payrollSettingsRepository.findFirstByOrderByIdAsc().orElseGet(this::createStartingPoint);
    }

    @Transactional(readOnly = true)
    public PayrollSettings currentOrNull() {
        return payrollSettingsRepository.findFirstByOrderByIdAsc().orElse(null);
    }

    /**
     * The rates a fresh installation starts with, every one of them marked unconfirmed.
     *
     * <p>Accounts are matched by code where the seeded chart has them. Where a code is missing —
     * which it will be on any database that predates this module, since {@code seedAccounts}
     * returns early once any account exists — the field is left empty rather than pointed at
     * something nearly right. A payroll posted to the wrong liability account is harder to find
     * than one that refused to post.
     */
    private PayrollSettings createStartingPoint() {
        PayrollSettings settings = PayrollSettings.builder()
                .confirmed(false)
                .pensionEmployeeRate(new BigDecimal("3.00"))
                .pensionEmployerRate(new BigDecimal("5.00"))
                .occupationalHazardRate(new BigDecimal("2.00"))
                .maternityEmployeeRate(new BigDecimal("0.30"))
                .maternityEmployerRate(new BigDecimal("0.30"))
                .medicalEmployeeRate(new BigDecimal("7.50"))
                .medicalEmployerRate(new BigDecimal("7.50"))
                .cbhiEmployeeRate(new BigDecimal("0.50"))
                .pensionDeductibleForPaye(true)
                .wagesExpenseAccountId(accountIdByCode("5002"))
                .employerContributionAccountId(accountIdByCode("5008"))
                .payePayableAccountId(accountIdByCode("2102"))
                .rssbPayableAccountId(accountIdByCode("2104"))
                .cbhiPayableAccountId(accountIdByCode("2105"))
                .netPayPayableAccountId(accountIdByCode("2103"))
                .build();
        settings.addBand(band(0, "0", "60000", "0.00"));
        settings.addBand(band(1, "60000", "100000", "10.00"));
        settings.addBand(band(2, "100000", "200000", "20.00"));
        settings.addBand(band(3, "200000", null, "30.00"));
        return payrollSettingsRepository.save(settings);
    }

    private static PayeBand band(int order, String floor, String ceiling, String rate) {
        return PayeBand.builder()
                .sortOrder(order)
                .bandFloor(new BigDecimal(floor))
                .bandCeiling(ceiling == null ? null : new BigDecimal(ceiling))
                .rate(new BigDecimal(rate))
                .build();
    }

    private Long accountIdByCode(String code) {
        return accountRepository.findByCodeIgnoreCase(code).map(Account::getId).orElse(null);
    }

    // ----- Editing -----

    @Transactional
    public PayrollSettings save(PayrollSettingsForm form) {
        PayrollSettings settings = current();

        settings.setPensionEmployeeRate(zero(form.getPensionEmployeeRate()));
        settings.setPensionEmployerRate(zero(form.getPensionEmployerRate()));
        settings.setOccupationalHazardRate(zero(form.getOccupationalHazardRate()));
        settings.setMaternityEmployeeRate(zero(form.getMaternityEmployeeRate()));
        settings.setMaternityEmployerRate(zero(form.getMaternityEmployerRate()));
        settings.setMedicalEmployeeRate(zero(form.getMedicalEmployeeRate()));
        settings.setMedicalEmployerRate(zero(form.getMedicalEmployerRate()));
        settings.setCbhiEmployeeRate(zero(form.getCbhiEmployeeRate()));
        settings.setContributionCeiling(form.getContributionCeiling());
        settings.setPensionDeductibleForPaye(form.pensionDeductibleValue());
        settings.setRateSource(trimToNull(form.getRateSource()));

        settings.setWagesExpenseAccountId(form.getWagesExpenseAccountId());
        settings.setEmployerContributionAccountId(form.getEmployerContributionAccountId());
        settings.setPayePayableAccountId(form.getPayePayableAccountId());
        settings.setRssbPayableAccountId(form.getRssbPayableAccountId());
        settings.setCbhiPayableAccountId(form.getCbhiPayableAccountId());
        settings.setNetPayPayableAccountId(form.getNetPayPayableAccountId());

        replaceBands(settings, form);

        // Approval attaches to figures, not to a screen, so changing any of them withdraws it.
        settings.setConfirmed(false);
        settings.setConfirmedBy(null);
        settings.setConfirmedAt(null);

        PayrollSettings saved = payrollSettingsRepository.save(settings);
        auditService.log(MODULE, "UPDATE_PAYROLL_SETTINGS", "payrollSettings#" + saved.getId(),
                "Rates edited — confirmation withdrawn");
        return saved;
    }

    /**
     * Bands are validated as a set rather than one at a time: a table with a gap in it, or with two
     * bands claiming the same franc, taxes nobody correctly and the fault is in the shape of the
     * table rather than in any one row.
     */
    private void replaceBands(PayrollSettings settings, PayrollSettingsForm form) {
        List<PayrollSettingsForm.Band> typed = new ArrayList<>(form.filledBands());
        if (typed.isEmpty()) {
            throw new IllegalArgumentException("A PAYE table needs at least one band");
        }
        typed.sort(Comparator.comparing(PayrollSettingsForm.Band::floorValue));

        BigDecimal expectedFloor = BigDecimal.ZERO;
        int openEnded = 0;
        for (PayrollSettingsForm.Band b : typed) {
            if (b.floorValue().compareTo(expectedFloor) != 0) {
                throw new IllegalArgumentException("The bands must run on from one another without a "
                        + "gap or an overlap — expected a band starting at "
                        + expectedFloor.toPlainString() + " but found one starting at "
                        + b.floorValue().toPlainString());
            }
            if (b.rateValue().signum() < 0 || b.rateValue().compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException("A band rate has to be between 0 and 100");
            }
            if (b.getBandCeiling() == null) {
                openEnded++;
            } else {
                if (b.getBandCeiling().compareTo(b.floorValue()) <= 0) {
                    throw new IllegalArgumentException("A band has to end above where it starts");
                }
                expectedFloor = b.getBandCeiling();
            }
        }
        if (openEnded != 1) {
            throw new IllegalArgumentException("Exactly one band must be left open at the top, so "
                    + "that every salary falls inside the table");
        }
        if (typed.get(typed.size() - 1).getBandCeiling() != null) {
            throw new IllegalArgumentException("The open band has to be the last one");
        }

        settings.getBands().clear();
        int order = 0;
        for (PayrollSettingsForm.Band b : typed) {
            settings.addBand(PayeBand.builder()
                    .bandFloor(b.floorValue())
                    .bandCeiling(b.getBandCeiling())
                    .rate(b.rateValue())
                    .sortOrder(order++)
                    .build());
        }
    }

    @Transactional
    public PayrollSettings confirm(String source, String username) {
        PayrollSettings settings = current();
        if (!settings.isAccountsComplete()) {
            throw new IllegalStateException("Every account a run posts to has to be chosen before "
                    + "these rates can be confirmed");
        }
        if (settings.getBands().isEmpty()) {
            throw new IllegalStateException("A PAYE table with no bands cannot be confirmed");
        }
        settings.setConfirmed(true);
        settings.setConfirmedBy(username);
        settings.setConfirmedAt(LocalDateTime.now());
        if (trimToNull(source) != null) {
            settings.setRateSource(source.trim());
        }
        PayrollSettings saved = payrollSettingsRepository.save(settings);
        auditService.log(MODULE, "CONFIRM_PAYROLL_RATES", "payrollSettings#" + saved.getId(),
                "Rates confirmed by " + username
                        + (saved.getRateSource() == null ? "" : " against " + saved.getRateSource()));
        return saved;
    }

    @Transactional
    public PayrollSettings withdrawConfirmation(String username) {
        PayrollSettings settings = current();
        settings.setConfirmed(false);
        settings.setConfirmedBy(null);
        settings.setConfirmedAt(null);
        PayrollSettings saved = payrollSettingsRepository.save(settings);
        auditService.log(MODULE, "WITHDRAW_PAYROLL_RATES", "payrollSettings#" + saved.getId(),
                "Confirmation withdrawn by " + username);
        return saved;
    }

    // ----- Support for the page -----

    @Transactional(readOnly = true)
    public List<Account> accounts() {
        return accountRepository.findByActiveTrueOrderByCodeAsc();
    }

    /** Codes this module expects but the chart may not have, so the page can say which are missing. */
    @Transactional(readOnly = true)
    public List<String> missingExpectedAccounts() {
        List<String> missing = new ArrayList<>();
        for (String code : List.of("2102", "2103", "2104", "2105", "5002", "5008")) {
            if (accountRepository.findByCodeIgnoreCase(code).isEmpty()) {
                missing.add(code);
            }
        }
        return missing;
    }

    public PayrollSettingsForm toForm(PayrollSettings settings) {
        PayrollSettingsForm form = new PayrollSettingsForm();
        form.setPensionEmployeeRate(settings.getPensionEmployeeRate());
        form.setPensionEmployerRate(settings.getPensionEmployerRate());
        form.setOccupationalHazardRate(settings.getOccupationalHazardRate());
        form.setMaternityEmployeeRate(settings.getMaternityEmployeeRate());
        form.setMaternityEmployerRate(settings.getMaternityEmployerRate());
        form.setMedicalEmployeeRate(settings.getMedicalEmployeeRate());
        form.setMedicalEmployerRate(settings.getMedicalEmployerRate());
        form.setCbhiEmployeeRate(settings.getCbhiEmployeeRate());
        form.setContributionCeiling(settings.getContributionCeiling());
        form.setPensionDeductibleForPaye(settings.isPensionDeductibleForPaye());
        form.setRateSource(settings.getRateSource());
        form.setWagesExpenseAccountId(settings.getWagesExpenseAccountId());
        form.setEmployerContributionAccountId(settings.getEmployerContributionAccountId());
        form.setPayePayableAccountId(settings.getPayePayableAccountId());
        form.setRssbPayableAccountId(settings.getRssbPayableAccountId());
        form.setCbhiPayableAccountId(settings.getCbhiPayableAccountId());
        form.setNetPayPayableAccountId(settings.getNetPayPayableAccountId());
        for (PayeBand band : settings.getBands()) {
            PayrollSettingsForm.Band row = new PayrollSettingsForm.Band();
            row.setBandFloor(band.getBandFloor());
            row.setBandCeiling(band.getBandCeiling());
            row.setRate(band.getRate());
            form.getBands().add(row);
        }
        // Spare rows so a band can be added without a second round trip.
        for (int i = 0; i < 2; i++) {
            form.getBands().add(new PayrollSettingsForm.Band());
        }
        return form;
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
}
