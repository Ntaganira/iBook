/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : DepreciationService.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Charging the depreciation the asset register works out
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.DepreciationRunForm;
import com.ntaganira.heritier.ibook.entity.*;
import com.ntaganira.heritier.ibook.enums.*;
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

@Service
public class DepreciationService {

    private static final String MODULE = "depreciation";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String ACCUMULATED_FALLBACK_CODE = "1509";
    private static final String EXPENSE_FALLBACK_CODE = "5006";

    private final DepreciationRunRepository runRepository;
    private final FixedAssetRepository assetRepository;
    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final NumberingSequenceRepository numberingSequenceRepository;
    private final CompanyRepository companyRepository;
    private final FixedAssetService fixedAssetService;
    private final AuditService auditService;

    public DepreciationService(DepreciationRunRepository runRepository,
                               FixedAssetRepository assetRepository,
                               AccountRepository accountRepository,
                               JournalEntryRepository journalEntryRepository,
                               NumberingSequenceRepository numberingSequenceRepository,
                               CompanyRepository companyRepository,
                               FixedAssetService fixedAssetService,
                               AuditService auditService) {
        this.runRepository = runRepository;
        this.assetRepository = assetRepository;
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.numberingSequenceRepository = numberingSequenceRepository;
        this.companyRepository = companyRepository;
        this.fixedAssetService = fixedAssetService;
        this.auditService = auditService;
    }

    // ----- Queries -----

    @Transactional(readOnly = true)
    public Page<DepreciationRun> list(String q, String status, Pageable pageable) {
        return runRepository.search(trimToNull(q), parseStatus(status), pageable);
    }

    @Transactional(readOnly = true)
    public DepreciationRun get(Long id) {
        return id == null ? null : runRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public String baseCurrency() {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElse(null);
        return company == null || company.getCurrencyCode() == null ? "RWF" : company.getCurrencyCode();
    }

    @Transactional(readOnly = true)
    public RunSummary summary() {
        List<DepreciationRun> posted = runRepository.findPostedNewestFirst();
        LocalDate lastPeriod = posted.isEmpty() ? null : posted.get(0).getPeriodEnd();
        return new RunSummary(
                runRepository.count(),
                runRepository.countByStatus(DepreciationRunStatus.DRAFT),
                runRepository.countByStatus(DepreciationRunStatus.POSTED),
                runRepository.countByStatus(DepreciationRunStatus.VOID),
                zero(runRepository.totalPosted()),
                lastPeriod,
                outstandingAt(LocalDate.now()));
    }

    /** Everything owed across the register today, whether or not a run has been prepared for it. */
    @Transactional(readOnly = true)
    public BigDecimal outstandingAt(LocalDate asOf) {
        BigDecimal total = BigDecimal.ZERO;
        for (PreviewRow row : preview(asOf)) {
            total = total.add(row.charge());
        }
        return total;
    }

    /**
     * What each in-service asset owes if depreciation is brought up to the given date. The figure is
     * always "due by that date less what has already been charged", so a first run after six months
     * catches up all six and a second run on the same date finds nothing left to charge.
     */
    @Transactional(readOnly = true)
    public List<PreviewRow> preview(LocalDate periodEnd) {
        List<PreviewRow> rows = new ArrayList<>();
        LocalDate date = periodEnd == null ? LocalDate.now() : periodEnd;
        for (FixedAsset asset : assetRepository.findByStatusOrderByAssetNoAsc(AssetStatus.ACTIVE)) {
            BigDecimal charge = chargeFor(asset, date);
            if (charge.signum() <= 0) {
                continue;
            }
            rows.add(new PreviewRow(asset.getId(), asset.getAssetNo(), asset.getName(),
                    asset.getCategory(), zero(asset.getAcquisitionCost()),
                    asset.getAccumulatedDepreciation(), charge,
                    asset.getAccumulatedDepreciation().add(charge),
                    asset.getExpenseAccountCode(), asset.getAccumulatedAccountCode()));
        }
        return rows;
    }

    private BigDecimal chargeFor(FixedAsset asset, LocalDate periodEnd) {
        if (asset == null || !asset.isDepreciating()) {
            return BigDecimal.ZERO;
        }
        BigDecimal due = fixedAssetService.chargeDueBy(asset, periodEnd);
        BigDecimal charge = due.subtract(asset.getAccumulatedDepreciation());
        return charge.signum() > 0 ? charge : BigDecimal.ZERO;
    }

    // ----- Create -----

    @Transactional
    public DepreciationRun create(DepreciationRunForm form, String username) {
        LocalDate periodEnd = form.periodEnd() == null ? LocalDate.now() : form.periodEnd();
        List<PreviewRow> rows = preview(periodEnd);
        if (rows.isEmpty()) {
            throw new IllegalStateException("Nothing is owed up to " + periodEnd);
        }

        DepreciationRun run = new DepreciationRun();
        run.setRunNo(nextRunNo());
        run.setPeriodEnd(periodEnd);
        run.setNotes(trimToNull(form.notes()));
        run.setStatus(DepreciationRunStatus.DRAFT);
        run.setCreatedBy(username);

        int sort = 0;
        for (PreviewRow row : rows) {
            FixedAsset asset = assetRepository.findById(row.assetId()).orElse(null);
            if (asset == null) {
                continue;
            }
            run.addEntry(DepreciationEntry.builder()
                    .assetId(asset.getId())
                    .assetNo(asset.getAssetNo())
                    .assetName(asset.getName())
                    .category(asset.getCategory())
                    .acquisitionCost(zero(asset.getAcquisitionCost()))
                    .openingAccumulated(asset.getAccumulatedDepreciation())
                    .charge(row.charge())
                    .closingAccumulated(row.closingAccumulated())
                    .expenseAccountId(asset.getExpenseAccountId())
                    .expenseAccountCode(asset.getExpenseAccountCode())
                    .accumulatedAccountId(asset.getAccumulatedAccountId())
                    .accumulatedAccountCode(asset.getAccumulatedAccountCode())
                    .sortOrder(sort++)
                    .build());
        }
        recalculate(run);

        DepreciationRun saved = runRepository.save(run);
        auditService.log(MODULE, "CREATE_RUN", "depreciationRun#" + saved.getId(),
                saved.getRunNo() + " to " + saved.getPeriodEnd());

        if (form.postNowValue()) {
            saved = post(saved.getId(), username);
        }
        return saved;
    }

    private void recalculate(DepreciationRun run) {
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        for (DepreciationEntry e : run.getEntries()) {
            total = total.add(zero(e.getCharge()));
            if (e.isCharged()) {
                count++;
            }
        }
        run.setTotalCharge(total);
        run.setAssetCount(count);
    }

    // ----- Posting -----

    /**
     * Charges the run. Every asset's share is worked out again here rather than trusting what the
     * draft recorded, so two drafts prepared over the same period cannot both charge it — whichever
     * posts second finds nothing left owing and charges nothing.
     */
    @Transactional
    public DepreciationRun post(Long id, String username) {
        DepreciationRun run = runRepository.findById(id).orElseThrow();
        if (run.getStatus() != DepreciationRunStatus.DRAFT) {
            throw new IllegalStateException("Only a draft run can be posted");
        }
        if (run.getEntries().isEmpty()) {
            throw new IllegalStateException("This run has nothing to charge");
        }

        for (DepreciationEntry entry : run.getEntries()) {
            FixedAsset asset = assetRepository.findById(entry.getAssetId()).orElse(null);
            if (asset == null || asset.getStatus() != AssetStatus.ACTIVE) {
                entry.setCharge(BigDecimal.ZERO);
                continue;
            }
            BigDecimal charge = chargeFor(asset, run.getPeriodEnd());
            entry.setOpeningAccumulated(asset.getAccumulatedDepreciation());
            entry.setCharge(charge);
            entry.setClosingAccumulated(asset.getAccumulatedDepreciation().add(charge));
            if (charge.signum() <= 0) {
                continue;
            }
            asset.setPostedAccumulated(zero(asset.getPostedAccumulated()).add(charge));
            asset.setDepreciatedTo(run.getPeriodEnd());
            assetRepository.save(asset);
        }

        recalculate(run);
        if (run.getTotalCharge().signum() <= 0) {
            throw new IllegalStateException("Nothing is owed any more — this run has been overtaken");
        }

        run.setJournalEntryId(postEntry(run, username, false));
        run.setStatus(DepreciationRunStatus.POSTED);
        run.setPostedAt(LocalDateTime.now());

        DepreciationRun saved = runRepository.save(run);
        auditService.log(MODULE, "POST_RUN", "depreciationRun#" + id,
                saved.getRunNo() + " charged " + saved.getTotalCharge().toPlainString()
                        + " across " + saved.getAssetCount() + " asset(s)");
        return saved;
    }

    /**
     * Writes a reversing entry and takes the charge back off each asset. The original entry and the
     * charges it recorded are left exactly as they were.
     */
    @Transactional
    public DepreciationRun voidRun(Long id, String reason, String username) {
        DepreciationRun run = runRepository.findById(id).orElseThrow();
        if (run.getStatus() != DepreciationRunStatus.POSTED) {
            throw new IllegalStateException("Only a posted run can be voided");
        }

        for (DepreciationEntry entry : run.getEntries()) {
            if (!entry.isCharged()) {
                continue;
            }
            FixedAsset asset = assetRepository.findById(entry.getAssetId()).orElse(null);
            if (asset == null) {
                continue;
            }
            BigDecimal back = zero(asset.getPostedAccumulated()).subtract(zero(entry.getCharge()));
            asset.setPostedAccumulated(back.signum() > 0 ? back : BigDecimal.ZERO);
            assetRepository.save(asset);
        }

        run.setReversalJournalEntryId(postEntry(run, username, true));
        run.setStatus(DepreciationRunStatus.VOID);
        run.setStoppedReason(trimToNull(reason));

        DepreciationRun saved = runRepository.save(run);
        auditService.log(MODULE, "VOID_RUN", "depreciationRun#" + id,
                saved.getRunNo() + (reason == null || reason.isBlank() ? "" : " — " + reason.trim()));
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        DepreciationRun run = runRepository.findById(id).orElse(null);
        if (run == null) {
            return;
        }
        if (run.getStatus() != DepreciationRunStatus.DRAFT) {
            throw new IllegalStateException("Only a draft run can be deleted");
        }
        runRepository.delete(run);
        auditService.log(MODULE, "DELETE_RUN", "depreciationRun#" + id, run.getRunNo());
    }

    /**
     * One entry for the whole run, with a debit and credit pair per account combination rather than
     * per asset, so a monthly charge over a large register stays readable. Rounding drift lands on
     * the last line so debits equal credits.
     */
    private Long postEntry(DepreciationRun run, String username, boolean reversing) {
        Map<String, BigDecimal> byPair = new LinkedHashMap<>();
        Map<String, Long[]> accountsFor = new LinkedHashMap<>();

        Account expenseFallback = accountRepository.findByCodeIgnoreCase(EXPENSE_FALLBACK_CODE).orElse(null);
        Account accumulatedFallback =
                accountRepository.findByCodeIgnoreCase(ACCUMULATED_FALLBACK_CODE).orElse(null);

        for (DepreciationEntry entry : run.getEntries()) {
            if (!entry.isCharged()) {
                continue;
            }
            Long expenseId = entry.getExpenseAccountId() != null ? entry.getExpenseAccountId()
                    : (expenseFallback == null ? null : expenseFallback.getId());
            Long accumulatedId = entry.getAccumulatedAccountId() != null
                    ? entry.getAccumulatedAccountId()
                    : (accumulatedFallback == null ? null : accumulatedFallback.getId());
            if (expenseId == null || accumulatedId == null) {
                continue;
            }
            String key = expenseId + ":" + accumulatedId;
            byPair.merge(key, zero(entry.getCharge()), BigDecimal::add);
            accountsFor.putIfAbsent(key, new Long[]{expenseId, accumulatedId});
        }
        if (byPair.isEmpty()) {
            return null;
        }

        String memo = (reversing ? "Reversal of depreciation — " : "Depreciation — ")
                + run.getRunNo() + " to " + run.getPeriodEnd();
        JournalEntry entry = JournalEntry.builder()
                .entryNo(nextJournalNo())
                .entryDate(run.getPeriodEnd())
                .type(JournalEntryType.ADJUSTMENT)
                .status(JournalEntryStatus.POSTED)
                .reference(run.getRunNo())
                .memo(memo)
                .createdBy(username)
                .build();

        int sort = 0;
        for (Map.Entry<String, BigDecimal> pair : byPair.entrySet()) {
            Long[] ids = accountsFor.get(pair.getKey());
            Account expense = accountRepository.findById(ids[0]).orElse(null);
            Account accumulated = accountRepository.findById(ids[1]).orElse(null);
            if (expense == null || accumulated == null) {
                continue;
            }
            BigDecimal amount = pair.getValue();
            Account debit = reversing ? accumulated : expense;
            Account credit = reversing ? expense : accumulated;
            entry.addLine(line(debit, run.getRunNo(), amount, BigDecimal.ZERO, sort++));
            entry.addLine(line(credit, run.getRunNo(), BigDecimal.ZERO, amount, sort++));
        }
        if (entry.getLines().isEmpty()) {
            return null;
        }

        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        for (JournalLine l : entry.getLines()) {
            debits = debits.add(zero(l.getDebit()));
            credits = credits.add(zero(l.getCredit()));
        }
        BigDecimal drift = debits.subtract(credits);
        if (drift.signum() != 0) {
            JournalLine last = entry.getLines().get(entry.getLines().size() - 1);
            if (drift.signum() > 0) {
                last.setCredit(zero(last.getCredit()).add(drift));
                credits = credits.add(drift);
            } else {
                last.setDebit(zero(last.getDebit()).add(drift.negate()));
                debits = debits.add(drift.negate());
            }
        }
        entry.setTotalDebits(debits);
        entry.setTotalCredits(credits);
        return journalEntryRepository.save(entry).getId();
    }

    private static JournalLine line(Account account, String memo, BigDecimal debit,
                                    BigDecimal credit, int sortOrder) {
        return JournalLine.builder()
                .accountId(account.getId())
                .accountCode(account.getCode())
                .accountName(account.getName())
                .memo(memo)
                .debit(debit)
                .credit(credit)
                .sortOrder(sortOrder)
                .build();
    }

    // ----- Helpers -----

    @Transactional(readOnly = true)
    public String previewNextNumber() {
        NumberingSequence seq = numberingSequenceRepository.findByDocType("DEPRECIATION").orElse(null);
        return seq == null ? "DEP-0001" : seq.previewNext();
    }

    private String nextRunNo() {
        NumberingSequence seq = numberingSequenceRepository.findByDocType("DEPRECIATION").orElse(null);
        if (seq != null) {
            long next = seq.getNextNumber();
            String prefix = seq.getPrefix() == null ? "DEP-" : seq.getPrefix();
            String suffix = seq.getSuffix() == null ? "" : seq.getSuffix();
            int padding = seq.getPadding() == 0 ? 4 : seq.getPadding();
            String number = prefix + String.format("%0" + padding + "d", next) + suffix;
            seq.setNextNumber(next + 1);
            numberingSequenceRepository.save(seq);
            return number;
        }
        return "DEP-" + LocalDate.now().getYear() + "-" + String.format("%05d", RANDOM.nextInt(100000));
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

    private static DepreciationRunStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return DepreciationRunStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
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

    public record PreviewRow(Long assetId, String assetNo, String assetName, String category,
                             BigDecimal acquisitionCost, BigDecimal openingAccumulated,
                             BigDecimal charge, BigDecimal closingAccumulated,
                             String expenseAccountCode, String accumulatedAccountCode) {
        public BigDecimal closingNbv() {
            return acquisitionCost.subtract(closingAccumulated);
        }
    }

    public record RunSummary(long all, long draft, long posted, long voided, BigDecimal totalPosted,
                             LocalDate lastPeriod, BigDecimal outstanding) {}
}
