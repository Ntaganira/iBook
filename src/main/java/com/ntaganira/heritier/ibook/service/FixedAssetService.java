/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : FixedAssetService.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : The fixed asset register and the depreciation it works out
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.FixedAssetForm;
import com.ntaganira.heritier.ibook.entity.*;
import com.ntaganira.heritier.ibook.enums.AssetStatus;
import com.ntaganira.heritier.ibook.enums.DepreciationMethod;
import com.ntaganira.heritier.ibook.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class FixedAssetService {

    private static final String MODULE = "fixed-assets";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String ASSET_ACCOUNT_CODE = "1501";
    private static final String ACCUMULATED_ACCOUNT_CODE = "1509";
    private static final String EXPENSE_ACCOUNT_CODE = "5006";

    private static final List<AssetStatus> LIVE =
            List.of(AssetStatus.DRAFT, AssetStatus.ACTIVE);

    private final FixedAssetRepository assetRepository;
    private final AccountRepository accountRepository;
    private final VendorRepository vendorRepository;
    private final NumberingSequenceRepository numberingSequenceRepository;
    private final CompanyRepository companyRepository;
    private final AuditService auditService;

    public FixedAssetService(FixedAssetRepository assetRepository,
                             AccountRepository accountRepository,
                             VendorRepository vendorRepository,
                             NumberingSequenceRepository numberingSequenceRepository,
                             CompanyRepository companyRepository,
                             AuditService auditService) {
        this.assetRepository = assetRepository;
        this.accountRepository = accountRepository;
        this.vendorRepository = vendorRepository;
        this.numberingSequenceRepository = numberingSequenceRepository;
        this.companyRepository = companyRepository;
        this.auditService = auditService;
    }

    // ----- Queries -----

    @Transactional(readOnly = true)
    public Page<FixedAsset> list(String q, String status, String category, Pageable pageable) {
        return assetRepository.search(trimToNull(q), parseStatus(status), trimToNull(category), pageable);
    }

    @Transactional(readOnly = true)
    public FixedAsset get(Long id) {
        return id == null ? null : assetRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<String> categories() {
        return assetRepository.distinctCategories();
    }

    @Transactional(readOnly = true)
    public String baseCurrency() {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElse(null);
        return company == null || company.getCurrencyCode() == null ? "RWF" : company.getCurrencyCode();
    }

    @Transactional(readOnly = true)
    public AssetSummary summary() {
        BigDecimal cost = zero(assetRepository.totalCostFor(LIVE));
        BigDecimal accumulated = zero(assetRepository.totalAccumulatedFor(LIVE));
        return new AssetSummary(
                assetRepository.count(),
                assetRepository.countByStatus(AssetStatus.DRAFT),
                assetRepository.countByStatus(AssetStatus.ACTIVE),
                assetRepository.countByStatus(AssetStatus.DISPOSED)
                        + assetRepository.countByStatus(AssetStatus.WRITTEN_OFF),
                cost, accumulated, cost.subtract(accumulated));
    }

    /** True when the chart is missing an account the register wants to default to. */
    @Transactional(readOnly = true)
    public boolean accountsMissing() {
        return accountRepository.findByCodeIgnoreCase(ASSET_ACCOUNT_CODE).isEmpty()
                || accountRepository.findByCodeIgnoreCase(ACCUMULATED_ACCOUNT_CODE).isEmpty()
                || accountRepository.findByCodeIgnoreCase(EXPENSE_ACCOUNT_CODE).isEmpty();
    }

    // ----- Depreciation, worked out but never posted -----

    /**
     * Year-by-year write-down from the date depreciation starts. Straight line spreads the
     * depreciable amount evenly; reducing balance takes a percentage of what is left each year and
     * is capped at the useful life, with the final year charging whatever brings the asset down to
     * its residual value — left to run, the method never reaches zero on its own.
     */
    @Transactional(readOnly = true)
    public List<ScheduleRow> scheduleFor(FixedAsset asset) {
        List<ScheduleRow> rows = new ArrayList<>();
        if (asset == null || !asset.isDepreciating() || asset.getUsefulLifeYears() <= 0) {
            return rows;
        }
        BigDecimal depreciable = asset.getDepreciableAmount();
        BigDecimal residual = zero(asset.getResidualValue());
        LocalDate start = asset.getDepreciationStart() == null
                ? asset.getAcquisitionDate() : asset.getDepreciationStart();
        int years = asset.getUsefulLifeYears();

        BigDecimal openingNbv = zero(asset.getAcquisitionCost());
        BigDecimal accumulated = BigDecimal.ZERO;

        for (int year = 1; year <= years; year++) {
            BigDecimal charge;
            if (asset.getDepreciationMethod() == DepreciationMethod.REDUCING_BALANCE) {
                charge = openingNbv.multiply(zero(asset.getDecliningRate()))
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            } else {
                charge = depreciable.divide(new BigDecimal(years), 2, RoundingMode.HALF_UP);
            }
            BigDecimal remaining = depreciable.subtract(accumulated);
            if (charge.compareTo(remaining) > 0) {
                charge = remaining;
            }
            // The last year absorbs the rounding, so the asset lands exactly on its residual value.
            if (year == years && charge.compareTo(remaining) < 0) {
                charge = remaining;
            }
            if (charge.signum() < 0) {
                charge = BigDecimal.ZERO;
            }
            accumulated = accumulated.add(charge);
            BigDecimal closingNbv = zero(asset.getAcquisitionCost()).subtract(accumulated);
            rows.add(new ScheduleRow(year, start.plusYears(year - 1L),
                    start.plusYears(year).minusDays(1), openingNbv, charge, accumulated, closingNbv));
            openingNbv = closingNbv;
            if (closingNbv.compareTo(residual) <= 0) {
                break;
            }
        }
        return rows;
    }

    /**
     * What should have been charged by a date, prorated by whole months within the year it falls in.
     * The register shows this beside what has actually been posted; the gap is what
     * {@code /assets/depreciation} will have to catch up.
     */
    @Transactional(readOnly = true)
    public BigDecimal chargeDueBy(FixedAsset asset, LocalDate asOf) {
        if (asset == null || !asset.isDepreciating()) {
            return BigDecimal.ZERO;
        }
        LocalDate date = asOf == null ? LocalDate.now() : asOf;
        LocalDate start = asset.getDepreciationStart() == null
                ? asset.getAcquisitionDate() : asset.getDepreciationStart();
        if (start == null || !date.isAfter(start)) {
            return BigDecimal.ZERO;
        }

        BigDecimal due = BigDecimal.ZERO;
        for (ScheduleRow row : scheduleFor(asset)) {
            if (!date.isBefore(row.periodEnd())) {
                due = due.add(row.charge());
                continue;
            }
            if (date.isAfter(row.periodStart())) {
                long months = ChronoUnit.MONTHS.between(row.periodStart(), date);
                if (months > 0) {
                    due = due.add(row.charge().multiply(new BigDecimal(months))
                            .divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP));
                }
            }
            break;
        }
        BigDecimal cap = asset.getDepreciableAmount();
        return due.compareTo(cap) > 0 ? cap : due;
    }

    @Transactional(readOnly = true)
    public AssetPosition positionFor(FixedAsset asset, LocalDate asOf) {
        BigDecimal due = chargeDueBy(asset, asOf);
        BigDecimal charged = asset.getAccumulatedDepreciation();
        BigDecimal behind = due.subtract(charged);
        return new AssetPosition(due, charged, behind.signum() > 0 ? behind : BigDecimal.ZERO,
                asset.getNetBookValue());
    }

    // ----- Create / update -----

    @Transactional
    public FixedAsset save(FixedAssetForm form, Long id, String username) {
        FixedAsset asset;
        if (id == null) {
            asset = new FixedAsset();
            asset.setAssetNo(nextAssetNo());
            asset.setCreatedBy(username);
            asset.setStatus(AssetStatus.DRAFT);
        } else {
            asset = assetRepository.findById(id).orElseThrow();
            if (!asset.isEditable()) {
                throw new IllegalStateException("A retired asset can no longer be edited");
            }
        }

        if (trimToNull(form.name()) == null) {
            throw new IllegalArgumentException("An asset needs a name");
        }
        DepreciationMethod method = parseMethod(form.depreciationMethod());
        if (method == DepreciationMethod.REDUCING_BALANCE
                && form.decliningRateOrZero().signum() <= 0) {
            throw new IllegalArgumentException("Reducing balance needs a rate above zero");
        }
        if (method != DepreciationMethod.NONE && form.usefulLifeValue() <= 0) {
            throw new IllegalArgumentException("Set a useful life of at least one year");
        }
        if (form.residualValueOrZero().compareTo(form.costValue()) > 0) {
            throw new IllegalArgumentException("Residual value cannot be more than the cost");
        }
        if (form.openingAccumulatedOrZero().compareTo(
                form.costValue().subtract(form.residualValueOrZero())) > 0) {
            throw new IllegalArgumentException(
                    "Depreciation already charged cannot exceed the depreciable amount");
        }

        asset.setName(form.name().trim());
        asset.setDescription(trimToNull(form.description()));
        asset.setCategory(trimToNull(form.category()));
        asset.setLocation(trimToNull(form.location()));
        asset.setCustodian(trimToNull(form.custodian()));
        asset.setSerialNumber(trimToNull(form.serialNumber()));
        asset.setTagNumber(trimToNull(form.tagNumber()));
        asset.setPurchaseReference(trimToNull(form.purchaseReference()));
        asset.setAcquisitionDate(form.acquisitionDate() == null
                ? LocalDate.now() : form.acquisitionDate());
        asset.setAcquisitionCost(form.costValue());
        asset.setResidualValue(form.residualValueOrZero());
        asset.setDepreciationMethod(method);
        asset.setUsefulLifeYears(method == DepreciationMethod.NONE ? 0 : form.usefulLifeValue());
        asset.setDecliningRate(method == DepreciationMethod.REDUCING_BALANCE
                ? form.decliningRateOrZero() : BigDecimal.ZERO);
        asset.setDepreciationStart(form.depreciationStart() == null
                ? asset.getAcquisitionDate() : form.depreciationStart());
        asset.setOpeningAccumulated(form.openingAccumulatedOrZero());
        asset.setNotes(trimToNull(form.notes()));

        Vendor vendor = form.vendorId() == null ? null
                : vendorRepository.findById(form.vendorId()).orElse(null);
        asset.setVendorId(vendor == null ? null : vendor.getId());
        asset.setVendorName(vendor == null ? null : vendor.getName());

        applyAccount(asset, form);

        FixedAsset saved = assetRepository.save(asset);
        auditService.log(MODULE, id == null ? "CREATE_ASSET" : "UPDATE_ASSET",
                "fixedAsset#" + saved.getId(), saved.getAssetNo() + " " + saved.getName());

        if (form.activateNowValue() && saved.getStatus() == AssetStatus.DRAFT) {
            saved = activate(saved.getId());
        }
        return saved;
    }

    private void applyAccount(FixedAsset asset, FixedAssetForm form) {
        Account assetAccount = resolve(form.assetAccountId(), ASSET_ACCOUNT_CODE);
        Account accumulated = resolve(form.accumulatedAccountId(), ACCUMULATED_ACCOUNT_CODE);
        Account expense = resolve(form.expenseAccountId(), EXPENSE_ACCOUNT_CODE);
        asset.setAssetAccountId(assetAccount == null ? null : assetAccount.getId());
        asset.setAssetAccountCode(assetAccount == null ? null : assetAccount.getCode());
        asset.setAssetAccountName(assetAccount == null ? null : assetAccount.getName());
        asset.setAccumulatedAccountId(accumulated == null ? null : accumulated.getId());
        asset.setAccumulatedAccountCode(accumulated == null ? null : accumulated.getCode());
        asset.setExpenseAccountId(expense == null ? null : expense.getId());
        asset.setExpenseAccountCode(expense == null ? null : expense.getCode());
    }

    private Account resolve(Long accountId, String fallbackCode) {
        if (accountId != null) {
            Account chosen = accountRepository.findById(accountId).orElse(null);
            if (chosen != null) {
                return chosen;
            }
        }
        return accountRepository.findByCodeIgnoreCase(fallbackCode).orElse(null);
    }

    @Transactional
    public FixedAsset activate(Long id) {
        FixedAsset asset = assetRepository.findById(id).orElseThrow();
        if (asset.isRetired()) {
            throw new IllegalStateException("A retired asset cannot be put back into service");
        }
        if (zero(asset.getAcquisitionCost()).signum() <= 0) {
            throw new IllegalStateException("An asset needs a cost before it goes into service");
        }
        asset.setStatus(AssetStatus.ACTIVE);
        FixedAsset saved = assetRepository.save(asset);
        auditService.log(MODULE, "ACTIVATE_ASSET", "fixedAsset#" + id,
                saved.getAssetNo() + " in service");
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        FixedAsset asset = assetRepository.findById(id).orElse(null);
        if (asset == null) {
            return;
        }
        if (asset.getStatus() != AssetStatus.DRAFT) {
            throw new IllegalStateException("Only a draft asset can be deleted");
        }
        if (zero(asset.getPostedAccumulated()).signum() != 0) {
            throw new IllegalStateException("This asset has depreciation posted against it");
        }
        assetRepository.delete(asset);
        auditService.log(MODULE, "DELETE_ASSET", "fixedAsset#" + id, asset.getAssetNo());
    }

    // ----- Helpers -----

    @Transactional(readOnly = true)
    public String previewNextNumber() {
        NumberingSequence seq = numberingSequenceRepository.findByDocType("FIXED_ASSET").orElse(null);
        return seq == null ? "FA-0001" : seq.previewNext();
    }

    private String nextAssetNo() {
        NumberingSequence seq = numberingSequenceRepository.findByDocType("FIXED_ASSET").orElse(null);
        if (seq != null) {
            long next = seq.getNextNumber();
            String prefix = seq.getPrefix() == null ? "FA-" : seq.getPrefix();
            String suffix = seq.getSuffix() == null ? "" : seq.getSuffix();
            int padding = seq.getPadding() == 0 ? 4 : seq.getPadding();
            String number = prefix + String.format("%0" + padding + "d", next) + suffix;
            seq.setNextNumber(next + 1);
            numberingSequenceRepository.save(seq);
            return number;
        }
        return "FA-" + LocalDate.now().getYear() + "-" + String.format("%05d", RANDOM.nextInt(100000));
    }

    private static AssetStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return AssetStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static DepreciationMethod parseMethod(String method) {
        if (method == null || method.isBlank()) {
            return DepreciationMethod.STRAIGHT_LINE;
        }
        try {
            return DepreciationMethod.valueOf(method.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return DepreciationMethod.STRAIGHT_LINE;
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

    public record ScheduleRow(int year, LocalDate periodStart, LocalDate periodEnd,
                              BigDecimal openingNbv, BigDecimal charge, BigDecimal accumulated,
                              BigDecimal closingNbv) {}

    public record AssetPosition(BigDecimal dueToDate, BigDecimal charged, BigDecimal notYetPosted,
                                BigDecimal netBookValue) {
        public boolean isBehind() {
            return notYetPosted.signum() > 0;
        }
    }

    public record AssetSummary(long all, long draft, long active, long retired,
                               BigDecimal totalCost, BigDecimal totalAccumulated,
                               BigDecimal netBookValue) {}
}
