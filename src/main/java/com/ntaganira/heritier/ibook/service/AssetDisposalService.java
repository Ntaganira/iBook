/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : AssetDisposalService.java
 * - Date      : 2026. 09. 19.
 * - User      : Hntaganira
 * - Desc      : Taking a fixed asset off the register and off the balance sheet
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.AssetDisposalForm;
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
import java.util.List;
import java.util.Locale;

@Service
public class AssetDisposalService {

    private static final String MODULE = "asset-disposals";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String GAIN_ACCOUNT_CODE = "4004";
    private static final String LOSS_ACCOUNT_CODE = "5007";
    private static final String ACCUMULATED_FALLBACK_CODE = "1509";
    private static final String EXPENSE_FALLBACK_CODE = "5006";
    private static final String ASSET_FALLBACK_CODE = "1501";

    private final AssetDisposalRepository disposalRepository;
    private final FixedAssetRepository assetRepository;
    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final NumberingSequenceRepository numberingSequenceRepository;
    private final CompanyRepository companyRepository;
    private final FixedAssetService fixedAssetService;
    private final AuditService auditService;

    public AssetDisposalService(AssetDisposalRepository disposalRepository,
                                FixedAssetRepository assetRepository,
                                AccountRepository accountRepository,
                                JournalEntryRepository journalEntryRepository,
                                NumberingSequenceRepository numberingSequenceRepository,
                                CompanyRepository companyRepository,
                                FixedAssetService fixedAssetService,
                                AuditService auditService) {
        this.disposalRepository = disposalRepository;
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
    public Page<AssetDisposal> list(String q, String status, Pageable pageable) {
        return disposalRepository.search(trimToNull(q), parseStatus(status), pageable);
    }

    @Transactional(readOnly = true)
    public AssetDisposal get(Long id) {
        return id == null ? null : disposalRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public String baseCurrency() {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElse(null);
        return company == null || company.getCurrencyCode() == null ? "RWF" : company.getCurrencyCode();
    }

    @Transactional(readOnly = true)
    public DisposalSummary summary() {
        return new DisposalSummary(
                disposalRepository.count(),
                disposalRepository.countByStatus(DisposalStatus.DRAFT),
                disposalRepository.countByStatus(DisposalStatus.POSTED),
                disposalRepository.countByStatus(DisposalStatus.VOID),
                zero(disposalRepository.totalProceeds()),
                zero(disposalRepository.totalGainOrLoss()));
    }

    /** Assets that can still be disposed of: in service and not already on a live disposal. */
    @Transactional(readOnly = true)
    public List<FixedAsset> disposableAssets() {
        List<FixedAsset> rows = new ArrayList<>();
        for (FixedAsset asset : assetRepository.findByStatusOrderByAssetNoAsc(AssetStatus.ACTIVE)) {
            if (disposalRepository.findLiveForAsset(asset.getId()).isEmpty()) {
                rows.add(asset);
            }
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public boolean gainLossAccountsMissing() {
        return accountRepository.findByCodeIgnoreCase(GAIN_ACCOUNT_CODE).isEmpty()
                || accountRepository.findByCodeIgnoreCase(LOSS_ACCOUNT_CODE).isEmpty();
    }

    /**
     * What disposing of an asset on a date for a price would come to. The depreciation still owed at
     * that date is charged by the disposal itself, so the net book value it is measured against is
     * the one the asset really has on the day it leaves, not the one the last run left behind.
     */
    @Transactional(readOnly = true)
    public DisposalPreview preview(FixedAsset asset, LocalDate disposalDate, BigDecimal proceeds) {
        if (asset == null) {
            return null;
        }
        LocalDate date = disposalDate == null ? LocalDate.now() : disposalDate;
        BigDecimal before = asset.getAccumulatedDepreciation();
        BigDecimal due = fixedAssetService.chargeDueBy(asset, date);
        BigDecimal catchUp = due.subtract(before);
        if (catchUp.signum() < 0) {
            catchUp = BigDecimal.ZERO;
        }
        BigDecimal accumulated = before.add(catchUp);
        BigDecimal cost = zero(asset.getAcquisitionCost());
        BigDecimal nbv = cost.subtract(accumulated);
        BigDecimal gainOrLoss = zero(proceeds).subtract(nbv);
        return new DisposalPreview(cost, before, catchUp, accumulated, nbv, zero(proceeds), gainOrLoss);
    }

    // ----- Create -----

    @Transactional
    public AssetDisposal save(AssetDisposalForm form, Long id, String username) {
        AssetDisposal disposal;
        if (id == null) {
            disposal = new AssetDisposal();
            disposal.setDisposalNo(nextDisposalNo());
            disposal.setCreatedBy(username);
            disposal.setStatus(DisposalStatus.DRAFT);
        } else {
            disposal = disposalRepository.findById(id).orElseThrow();
            if (!disposal.isDraft()) {
                throw new IllegalStateException("A posted disposal can no longer be edited");
            }
        }

        FixedAsset asset = assetRepository.findById(form.assetId()).orElseThrow();
        if (asset.getStatus() != AssetStatus.ACTIVE) {
            throw new IllegalStateException(asset.getAssetNo() + " is not in service");
        }
        for (AssetDisposal other : disposalRepository.findLiveForAsset(asset.getId())) {
            if (!other.getId().equals(disposal.getId())) {
                throw new IllegalStateException(asset.getAssetNo()
                        + " is already on disposal " + other.getDisposalNo());
            }
        }
        LocalDate date = form.disposalDate() == null ? LocalDate.now() : form.disposalDate();
        if (asset.getAcquisitionDate() != null && date.isBefore(asset.getAcquisitionDate())) {
            throw new IllegalArgumentException("An asset cannot be disposed of before it was acquired");
        }
        if (form.proceedsValue().signum() < 0) {
            throw new IllegalArgumentException("Proceeds cannot be negative");
        }

        DisposalMethod method = parseMethod(form.method());
        DisposalPreview p = preview(asset, date, form.proceedsValue());

        disposal.setAssetId(asset.getId());
        disposal.setAssetNo(asset.getAssetNo());
        disposal.setAssetName(asset.getName());
        disposal.setDisposalDate(date);
        disposal.setMethod(method);
        disposal.setBuyer(trimToNull(form.buyer()));
        disposal.setReference(trimToNull(form.reference()));
        disposal.setProceeds(form.proceedsValue());
        disposal.setNotes(trimToNull(form.notes()));
        applyPreview(disposal, p);
        applyAccounts(disposal, form);

        AssetDisposal saved = disposalRepository.save(disposal);
        auditService.log(MODULE, id == null ? "CREATE_DISPOSAL" : "UPDATE_DISPOSAL",
                "assetDisposal#" + saved.getId(),
                saved.getDisposalNo() + " " + saved.getAssetNo());

        if (form.postNowValue() && saved.isDraft()) {
            saved = post(saved.getId(), username);
        }
        return saved;
    }

    private void applyPreview(AssetDisposal disposal, DisposalPreview p) {
        disposal.setCostAtDisposal(p.cost());
        disposal.setAccumulatedBefore(p.accumulatedBefore());
        disposal.setCatchUpDepreciation(p.catchUp());
        disposal.setAccumulatedAtDisposal(p.accumulatedAtDisposal());
        disposal.setNetBookValue(p.netBookValue());
        disposal.setGainOrLoss(p.gainOrLoss());
    }

    private void applyAccounts(AssetDisposal disposal, AssetDisposalForm form) {
        Account proceedsAccount = form.proceedsAccountId() == null ? null
                : accountRepository.findById(form.proceedsAccountId()).orElse(null);
        Account gain = resolve(form.gainAccountId(), GAIN_ACCOUNT_CODE);
        Account loss = resolve(form.lossAccountId(), LOSS_ACCOUNT_CODE);
        disposal.setProceedsAccountId(proceedsAccount == null ? null : proceedsAccount.getId());
        disposal.setProceedsAccountCode(proceedsAccount == null ? null : proceedsAccount.getCode());
        disposal.setGainAccountId(gain == null ? null : gain.getId());
        disposal.setGainAccountCode(gain == null ? null : gain.getCode());
        disposal.setLossAccountId(loss == null ? null : loss.getId());
        disposal.setLossAccountCode(loss == null ? null : loss.getCode());
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

    // ----- Posting -----

    /**
     * Takes the asset off the books in one entry: the depreciation still owed at the disposal date,
     * then the accumulated depreciation and the cost reversed out, the proceeds brought in, and
     * whatever is left over recognised as a gain or a loss. The figures are worked out again here
     * rather than trusting the draft, because a depreciation run between the two would change them.
     */
    @Transactional
    public AssetDisposal post(Long id, String username) {
        AssetDisposal disposal = disposalRepository.findById(id).orElseThrow();
        if (!disposal.isDraft()) {
            throw new IllegalStateException("Only a draft disposal can be posted");
        }
        FixedAsset asset = assetRepository.findById(disposal.getAssetId()).orElseThrow();
        if (asset.getStatus() != AssetStatus.ACTIVE) {
            throw new IllegalStateException(asset.getAssetNo() + " is no longer in service");
        }

        DisposalPreview p = preview(asset, disposal.getDisposalDate(), disposal.getProceeds());
        applyPreview(disposal, p);

        if (p.gainOrLoss().signum() > 0 && disposal.getGainAccountId() == null) {
            throw new IllegalStateException("Choose the account the gain should go to");
        }
        if (p.gainOrLoss().signum() < 0 && disposal.getLossAccountId() == null) {
            throw new IllegalStateException("Choose the account the loss should go to");
        }
        if (disposal.getProceeds().signum() > 0 && disposal.getProceedsAccountId() == null) {
            throw new IllegalStateException("Choose where the proceeds were received");
        }

        disposal.setJournalEntryId(postEntry(disposal, asset, username, false));

        asset.setPostedAccumulated(zero(asset.getPostedAccumulated()).add(p.catchUp()));
        asset.setStatus(disposal.getMethod().resultingStatus());
        asset.setDisposedDate(disposal.getDisposalDate());
        asset.setDisposalProceeds(disposal.getProceeds());
        assetRepository.save(asset);

        disposal.setStatus(DisposalStatus.POSTED);
        disposal.setPostedAt(LocalDateTime.now());
        AssetDisposal saved = disposalRepository.save(disposal);
        auditService.log(MODULE, "POST_DISPOSAL", "assetDisposal#" + id,
                saved.getDisposalNo() + " " + saved.getAssetNo()
                        + " " + (saved.isGain() ? "gain " : saved.isLoss() ? "loss " : "")
                        + saved.getGainOrLoss().abs().toPlainString());
        return saved;
    }

    /**
     * Puts the asset back on the register and writes a reversing entry. The catch-up depreciation
     * the disposal charged is taken back off too, so the asset returns exactly as it left.
     */
    @Transactional
    public AssetDisposal voidDisposal(Long id, String reason, String username) {
        AssetDisposal disposal = disposalRepository.findById(id).orElseThrow();
        if (!disposal.isPosted()) {
            throw new IllegalStateException("Only a posted disposal can be voided");
        }
        FixedAsset asset = assetRepository.findById(disposal.getAssetId()).orElse(null);
        if (asset != null) {
            BigDecimal back = zero(asset.getPostedAccumulated())
                    .subtract(zero(disposal.getCatchUpDepreciation()));
            asset.setPostedAccumulated(back.signum() > 0 ? back : BigDecimal.ZERO);
            asset.setStatus(AssetStatus.ACTIVE);
            asset.setDisposedDate(null);
            asset.setDisposalProceeds(null);
            assetRepository.save(asset);
            disposal.setReversalJournalEntryId(postEntry(disposal, asset, username, true));
        }

        disposal.setStatus(DisposalStatus.VOID);
        disposal.setStoppedReason(trimToNull(reason));
        AssetDisposal saved = disposalRepository.save(disposal);
        auditService.log(MODULE, "VOID_DISPOSAL", "assetDisposal#" + id,
                saved.getDisposalNo() + (reason == null || reason.isBlank() ? "" : " — " + reason.trim()));
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        AssetDisposal disposal = disposalRepository.findById(id).orElse(null);
        if (disposal == null) {
            return;
        }
        if (!disposal.isDraft()) {
            throw new IllegalStateException("Only a draft disposal can be deleted");
        }
        disposalRepository.delete(disposal);
        auditService.log(MODULE, "DELETE_DISPOSAL", "assetDisposal#" + id, disposal.getDisposalNo());
    }

    private Long postEntry(AssetDisposal disposal, FixedAsset asset, String username,
                           boolean reversing) {
        Account assetAccount = resolve(asset.getAssetAccountId(), ASSET_FALLBACK_CODE);
        Account accumulated = resolve(asset.getAccumulatedAccountId(), ACCUMULATED_FALLBACK_CODE);
        Account expense = resolve(asset.getExpenseAccountId(), EXPENSE_FALLBACK_CODE);
        if (assetAccount == null || accumulated == null) {
            return null;
        }

        JournalEntry entry = JournalEntry.builder()
                .entryNo(nextJournalNo())
                .entryDate(disposal.getDisposalDate())
                .type(JournalEntryType.ADJUSTMENT)
                .status(JournalEntryStatus.POSTED)
                .reference(disposal.getDisposalNo())
                .memo((reversing ? "Reversal of disposal — " : "Disposal — ")
                        + disposal.getDisposalNo() + " " + disposal.getAssetNo()
                        + " " + disposal.getAssetName())
                .createdBy(username)
                .build();

        int sort = 0;
        BigDecimal catchUp = zero(disposal.getCatchUpDepreciation());
        if (catchUp.signum() > 0 && expense != null) {
            entry.addLine(line(reversing ? accumulated : expense, disposal.getDisposalNo(),
                    catchUp, BigDecimal.ZERO, sort++));
            entry.addLine(line(reversing ? expense : accumulated, disposal.getDisposalNo(),
                    BigDecimal.ZERO, catchUp, sort++));
        }

        BigDecimal accumulatedTotal = zero(disposal.getAccumulatedAtDisposal());
        if (accumulatedTotal.signum() > 0) {
            entry.addLine(line(accumulated, disposal.getDisposalNo(),
                    reversing ? BigDecimal.ZERO : accumulatedTotal,
                    reversing ? accumulatedTotal : BigDecimal.ZERO, sort++));
        }
        BigDecimal cost = zero(disposal.getCostAtDisposal());
        if (cost.signum() > 0) {
            entry.addLine(line(assetAccount, disposal.getDisposalNo(),
                    reversing ? cost : BigDecimal.ZERO,
                    reversing ? BigDecimal.ZERO : cost, sort++));
        }

        BigDecimal proceeds = zero(disposal.getProceeds());
        if (proceeds.signum() > 0 && disposal.getProceedsAccountId() != null) {
            Account into = accountRepository.findById(disposal.getProceedsAccountId()).orElse(null);
            if (into != null) {
                entry.addLine(line(into, disposal.getDisposalNo(),
                        reversing ? BigDecimal.ZERO : proceeds,
                        reversing ? proceeds : BigDecimal.ZERO, sort++));
            }
        }

        BigDecimal gainOrLoss = zero(disposal.getGainOrLoss());
        if (gainOrLoss.signum() > 0 && disposal.getGainAccountId() != null) {
            Account gain = accountRepository.findById(disposal.getGainAccountId()).orElse(null);
            if (gain != null) {
                entry.addLine(line(gain, disposal.getDisposalNo(),
                        reversing ? gainOrLoss : BigDecimal.ZERO,
                        reversing ? BigDecimal.ZERO : gainOrLoss, sort++));
            }
        } else if (gainOrLoss.signum() < 0 && disposal.getLossAccountId() != null) {
            Account loss = accountRepository.findById(disposal.getLossAccountId()).orElse(null);
            if (loss != null) {
                BigDecimal amount = gainOrLoss.negate();
                entry.addLine(line(loss, disposal.getDisposalNo(),
                        reversing ? BigDecimal.ZERO : amount,
                        reversing ? amount : BigDecimal.ZERO, sort++));
            }
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
        NumberingSequence seq = numberingSequenceRepository.findByDocType("DISPOSAL").orElse(null);
        return seq == null ? "DIS-0001" : seq.previewNext();
    }

    private String nextDisposalNo() {
        NumberingSequence seq = numberingSequenceRepository.findByDocType("DISPOSAL").orElse(null);
        if (seq != null) {
            long next = seq.getNextNumber();
            String prefix = seq.getPrefix() == null ? "DIS-" : seq.getPrefix();
            String suffix = seq.getSuffix() == null ? "" : seq.getSuffix();
            int padding = seq.getPadding() == 0 ? 4 : seq.getPadding();
            String number = prefix + String.format("%0" + padding + "d", next) + suffix;
            seq.setNextNumber(next + 1);
            numberingSequenceRepository.save(seq);
            return number;
        }
        return "DIS-" + LocalDate.now().getYear() + "-" + String.format("%05d", RANDOM.nextInt(100000));
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

    private static DisposalStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return DisposalStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static DisposalMethod parseMethod(String method) {
        if (method == null || method.isBlank()) {
            return DisposalMethod.SOLD;
        }
        try {
            return DisposalMethod.valueOf(method.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return DisposalMethod.SOLD;
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

    public record DisposalPreview(BigDecimal cost, BigDecimal accumulatedBefore, BigDecimal catchUp,
                                  BigDecimal accumulatedAtDisposal, BigDecimal netBookValue,
                                  BigDecimal proceeds, BigDecimal gainOrLoss) {
        public boolean isGain() {
            return gainOrLoss.signum() > 0;
        }

        public boolean isLoss() {
            return gainOrLoss.signum() < 0;
        }

        public BigDecimal lossAmount() {
            return isLoss() ? gainOrLoss.negate() : BigDecimal.ZERO;
        }
    }

    public record DisposalSummary(long all, long draft, long posted, long voided,
                                  BigDecimal totalProceeds, BigDecimal totalGainOrLoss) {}
}
