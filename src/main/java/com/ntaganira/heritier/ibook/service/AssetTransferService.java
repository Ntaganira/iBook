/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : AssetTransferService.java
 * - Date      : 2026. 09. 19.
 * - User      : Hntaganira
 * - Desc      : Moving a fixed asset between locations, custodians and balance sheet accounts
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.AssetTransferForm;
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
import java.util.Objects;

@Service
public class AssetTransferService {

    private static final String MODULE = "asset-transfers";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String ASSET_FALLBACK_CODE = "1501";
    private static final String ACCUMULATED_FALLBACK_CODE = "1509";

    private final AssetTransferRepository transferRepository;
    private final FixedAssetRepository assetRepository;
    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final NumberingSequenceRepository numberingSequenceRepository;
    private final CompanyRepository companyRepository;
    private final AuditService auditService;

    public AssetTransferService(AssetTransferRepository transferRepository,
                                FixedAssetRepository assetRepository,
                                AccountRepository accountRepository,
                                JournalEntryRepository journalEntryRepository,
                                NumberingSequenceRepository numberingSequenceRepository,
                                CompanyRepository companyRepository,
                                AuditService auditService) {
        this.transferRepository = transferRepository;
        this.assetRepository = assetRepository;
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.numberingSequenceRepository = numberingSequenceRepository;
        this.companyRepository = companyRepository;
        this.auditService = auditService;
    }

    // ----- Queries -----

    @Transactional(readOnly = true)
    public Page<AssetTransfer> list(String q, String status, Pageable pageable) {
        return transferRepository.search(trimToNull(q), parseStatus(status), pageable);
    }

    @Transactional(readOnly = true)
    public AssetTransfer get(Long id) {
        return id == null ? null : transferRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<AssetTransfer> historyFor(Long assetId) {
        return assetId == null ? List.of()
                : transferRepository.findByAssetIdOrderByTransferDateDescIdDesc(assetId);
    }

    @Transactional(readOnly = true)
    public String baseCurrency() {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElse(null);
        return company == null || company.getCurrencyCode() == null ? "RWF" : company.getCurrencyCode();
    }

    @Transactional(readOnly = true)
    public TransferSummary summary() {
        return new TransferSummary(
                transferRepository.count(),
                transferRepository.countByStatus(AssetTransferStatus.DRAFT),
                transferRepository.countByStatus(AssetTransferStatus.COMPLETED),
                transferRepository.countByStatus(AssetTransferStatus.CANCELLED),
                transferRepository.countByStatus(AssetTransferStatus.VOID),
                transferRepository.countReclassifications());
    }

    /** Assets that can be moved: in service and not already waiting on a draft transfer. */
    @Transactional(readOnly = true)
    public List<FixedAsset> movableAssets() {
        List<FixedAsset> rows = new ArrayList<>();
        for (FixedAsset asset : assetRepository.findByStatusOrderByAssetNoAsc(AssetStatus.ACTIVE)) {
            if (transferRepository.findDraftsForAsset(asset.getId()).isEmpty()) {
                rows.add(asset);
            }
        }
        return rows;
    }

    /** Locations already in use, so a move can pick one instead of retyping it. */
    @Transactional(readOnly = true)
    public List<String> knownLocations() {
        return assetRepository.distinctLocations();
    }

    // ----- Create / update -----

    @Transactional
    public AssetTransfer save(AssetTransferForm form, Long id, String username) {
        AssetTransfer transfer;
        if (id == null) {
            transfer = new AssetTransfer();
            transfer.setTransferNo(nextTransferNo());
            transfer.setCreatedBy(username);
            transfer.setStatus(AssetTransferStatus.DRAFT);
        } else {
            transfer = transferRepository.findById(id).orElseThrow();
            if (!transfer.isDraft()) {
                throw new IllegalStateException("A completed transfer can no longer be edited");
            }
        }

        FixedAsset asset = assetRepository.findById(form.assetId()).orElseThrow();
        if (asset.getStatus() != AssetStatus.ACTIVE) {
            throw new IllegalStateException(asset.getAssetNo() + " is not in service");
        }
        for (AssetTransfer other : transferRepository.findDraftsForAsset(asset.getId())) {
            if (!other.getId().equals(transfer.getId())) {
                throw new IllegalStateException(asset.getAssetNo()
                        + " is already waiting on transfer " + other.getTransferNo());
            }
        }
        LocalDate date = form.transferDate() == null ? LocalDate.now() : form.transferDate();
        if (asset.getAcquisitionDate() != null && date.isBefore(asset.getAcquisitionDate())) {
            throw new IllegalArgumentException("An asset cannot be moved before it was acquired");
        }

        transfer.setAssetId(asset.getId());
        transfer.setAssetNo(asset.getAssetNo());
        transfer.setAssetName(asset.getName());
        transfer.setTransferDate(date);
        transfer.setReference(trimToNull(form.reference()));
        transfer.setReason(trimToNull(form.reason()));
        transfer.setNotes(trimToNull(form.notes()));
        applyMove(transfer, asset, form);

        if (!changesAnything(transfer)) {
            throw new IllegalArgumentException(
                    "This transfer moves nothing — change the location, the custodian or an account");
        }

        AssetTransfer saved = transferRepository.save(transfer);
        auditService.log(MODULE, id == null ? "CREATE_TRANSFER" : "UPDATE_TRANSFER",
                "assetTransfer#" + saved.getId(),
                saved.getTransferNo() + " " + saved.getAssetNo());

        if (form.completeNowValue() && saved.isDraft()) {
            saved = complete(saved.getId(), username);
        }
        return saved;
    }

    /**
     * Blank means "leave it where it is": a transfer form asks where the asset is going, so an empty
     * box is an unchanged field rather than an instruction to wipe one.
     */
    private void applyMove(AssetTransfer transfer, FixedAsset asset, AssetTransferForm form) {
        transfer.setFromLocation(trimToNull(asset.getLocation()));
        transfer.setFromCustodian(trimToNull(asset.getCustodian()));
        String toLocation = trimToNull(form.toLocation());
        String toCustodian = trimToNull(form.toCustodian());
        transfer.setToLocation(toLocation == null ? transfer.getFromLocation() : toLocation);
        transfer.setToCustodian(toCustodian == null ? transfer.getFromCustodian() : toCustodian);

        Account fromAsset = resolve(asset.getAssetAccountId(), ASSET_FALLBACK_CODE);
        Account fromAccumulated = resolve(asset.getAccumulatedAccountId(), ACCUMULATED_FALLBACK_CODE);
        Account toAsset = form.toAssetAccountId() == null ? fromAsset
                : accountRepository.findById(form.toAssetAccountId()).orElse(fromAsset);
        Account toAccumulated = form.toAccumulatedAccountId() == null ? fromAccumulated
                : accountRepository.findById(form.toAccumulatedAccountId()).orElse(fromAccumulated);
        requireAssetAccount(toAsset);
        requireAssetAccount(toAccumulated);

        transfer.setFromAssetAccountId(fromAsset == null ? null : fromAsset.getId());
        transfer.setFromAssetAccountCode(fromAsset == null ? null : fromAsset.getCode());
        transfer.setToAssetAccountId(toAsset == null ? null : toAsset.getId());
        transfer.setToAssetAccountCode(toAsset == null ? null : toAsset.getCode());
        transfer.setFromAccumulatedAccountId(fromAccumulated == null ? null : fromAccumulated.getId());
        transfer.setFromAccumulatedAccountCode(fromAccumulated == null ? null : fromAccumulated.getCode());
        transfer.setToAccumulatedAccountId(toAccumulated == null ? null : toAccumulated.getId());
        transfer.setToAccumulatedAccountCode(toAccumulated == null ? null : toAccumulated.getCode());

        transfer.setCostAtTransfer(zero(asset.getAcquisitionCost()));
        transfer.setAccumulatedAtTransfer(asset.getAccumulatedDepreciation());
        transfer.setNetBookValue(asset.getNetBookValue());
    }

    private static boolean changesAnything(AssetTransfer transfer) {
        return transfer.isMovingLocation() || transfer.isMovingCustodian()
                || transfer.isMovingAccounts();
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

    /**
     * The picker only offers asset accounts, but a hand-made request could name any of them, and
     * moving an asset's cost into revenue posts a nonsense entry rather than failing.
     */
    private static void requireAssetAccount(Account account) {
        if (account != null && account.getType() != AccountType.ASSET) {
            throw new IllegalArgumentException(account.getCode() + " " + account.getName()
                    + " is not an asset account");
        }
    }

    // ----- Lifecycle -----

    /**
     * Moving an asset between locations or custodians changes nothing on the balance sheet — the same
     * asset sits in the same account at the same cost — so no journal entry is written. Moving it
     * between accounts does: the cost and the accumulated depreciation it has collected are both
     * carried across, otherwise the old accumulated account would be left holding depreciation for an
     * asset that no longer sits in its cost account.
     */
    @Transactional
    public AssetTransfer complete(Long id, String username) {
        AssetTransfer transfer = transferRepository.findById(id).orElseThrow();
        if (!transfer.isDraft()) {
            throw new IllegalStateException("Only a draft transfer can be completed");
        }
        FixedAsset asset = assetRepository.findById(transfer.getAssetId()).orElseThrow();
        if (asset.getStatus() != AssetStatus.ACTIVE) {
            throw new IllegalStateException(asset.getAssetNo() + " is no longer in service");
        }

        // Where the asset actually is now, not where it was when the draft was raised.
        transfer.setFromLocation(trimToNull(asset.getLocation()));
        transfer.setFromCustodian(trimToNull(asset.getCustodian()));
        Account fromAsset = resolve(asset.getAssetAccountId(), ASSET_FALLBACK_CODE);
        Account fromAccumulated = resolve(asset.getAccumulatedAccountId(), ACCUMULATED_FALLBACK_CODE);
        transfer.setFromAssetAccountId(fromAsset == null ? null : fromAsset.getId());
        transfer.setFromAssetAccountCode(fromAsset == null ? null : fromAsset.getCode());
        transfer.setFromAccumulatedAccountId(fromAccumulated == null ? null : fromAccumulated.getId());
        transfer.setFromAccumulatedAccountCode(fromAccumulated == null ? null : fromAccumulated.getCode());
        transfer.setCostAtTransfer(zero(asset.getAcquisitionCost()));
        transfer.setAccumulatedAtTransfer(asset.getAccumulatedDepreciation());
        transfer.setNetBookValue(asset.getNetBookValue());

        if (!changesAnything(transfer)) {
            throw new IllegalStateException(
                    "The asset is already where this transfer would put it");
        }

        boolean reclassifying = transfer.isMovingAccounts();
        transfer.setReclassified(reclassifying);
        if (reclassifying) {
            if (transfer.getToAssetAccountId() == null || transfer.getToAccumulatedAccountId() == null) {
                throw new IllegalStateException("Choose both accounts the asset is moving into");
            }
            transfer.setJournalEntryId(postEntry(transfer, username, false));
        }

        asset.setLocation(transfer.getToLocation());
        asset.setCustodian(transfer.getToCustodian());
        applyAccountsToAsset(asset, transfer.getToAssetAccountId(), transfer.getToAccumulatedAccountId());
        assetRepository.save(asset);

        transfer.setStatus(AssetTransferStatus.COMPLETED);
        transfer.setCompletedAt(LocalDateTime.now());
        AssetTransfer saved = transferRepository.save(transfer);
        auditService.log(MODULE, "COMPLETE_TRANSFER", "assetTransfer#" + id,
                saved.getTransferNo() + " " + saved.getAssetNo()
                        + (reclassifying ? " reclassified to " + saved.getToAssetAccountCode() : ""));
        return saved;
    }

    private void applyAccountsToAsset(FixedAsset asset, Long assetAccountId, Long accumulatedAccountId) {
        Account assetAccount = assetAccountId == null ? null
                : accountRepository.findById(assetAccountId).orElse(null);
        Account accumulated = accumulatedAccountId == null ? null
                : accountRepository.findById(accumulatedAccountId).orElse(null);
        if (assetAccount != null) {
            asset.setAssetAccountId(assetAccount.getId());
            asset.setAssetAccountCode(assetAccount.getCode());
            asset.setAssetAccountName(assetAccount.getName());
        }
        if (accumulated != null) {
            asset.setAccumulatedAccountId(accumulated.getId());
            asset.setAccumulatedAccountCode(accumulated.getCode());
        }
    }

    /**
     * Puts the asset back where it came from and reverses the reclassification if there was one. A
     * later completed move of the same asset blocks this: unwinding an earlier one would stamp the
     * asset with a location it has already left.
     */
    @Transactional
    public AssetTransfer voidTransfer(Long id, String reason, String username) {
        AssetTransfer transfer = transferRepository.findById(id).orElseThrow();
        if (!transfer.isCompleted()) {
            throw new IllegalStateException("Only a completed transfer can be voided");
        }
        List<AssetTransfer> later = transferRepository.findLaterCompleted(
                transfer.getAssetId(), transfer.getId(), transfer.getTransferDate());
        if (!later.isEmpty()) {
            throw new IllegalStateException(transfer.getAssetNo() + " has moved again since, on "
                    + later.get(0).getTransferNo() + " — void that first");
        }

        FixedAsset asset = assetRepository.findById(transfer.getAssetId()).orElse(null);
        if (asset != null) {
            asset.setLocation(transfer.getFromLocation());
            asset.setCustodian(transfer.getFromCustodian());
            applyAccountsToAsset(asset, transfer.getFromAssetAccountId(),
                    transfer.getFromAccumulatedAccountId());
            assetRepository.save(asset);
        }
        if (transfer.getJournalEntryId() != null) {
            transfer.setReversalJournalEntryId(postEntry(transfer, username, true));
        }

        transfer.setStatus(AssetTransferStatus.VOID);
        transfer.setStoppedReason(trimToNull(reason));
        AssetTransfer saved = transferRepository.save(transfer);
        auditService.log(MODULE, "VOID_TRANSFER", "assetTransfer#" + id,
                saved.getTransferNo() + (reason == null || reason.isBlank() ? "" : " — " + reason.trim()));
        return saved;
    }

    @Transactional
    public AssetTransfer cancel(Long id, String reason) {
        AssetTransfer transfer = transferRepository.findById(id).orElseThrow();
        if (!transfer.isDraft()) {
            throw new IllegalStateException("Only a draft transfer can be cancelled");
        }
        transfer.setStatus(AssetTransferStatus.CANCELLED);
        transfer.setStoppedReason(trimToNull(reason));
        AssetTransfer saved = transferRepository.save(transfer);
        auditService.log(MODULE, "CANCEL_TRANSFER", "assetTransfer#" + id, saved.getTransferNo());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        AssetTransfer transfer = transferRepository.findById(id).orElse(null);
        if (transfer == null) {
            return;
        }
        if (!transfer.isDraft() && !transfer.isCancelled()) {
            throw new IllegalStateException("Only a draft or cancelled transfer can be deleted");
        }
        transferRepository.delete(transfer);
        auditService.log(MODULE, "DELETE_TRANSFER", "assetTransfer#" + id, transfer.getTransferNo());
    }

    // ----- Posting -----

    private Long postEntry(AssetTransfer transfer, String username, boolean reversing) {
        Account fromAsset = lookup(transfer.getFromAssetAccountId());
        Account toAsset = lookup(transfer.getToAssetAccountId());
        Account fromAccumulated = lookup(transfer.getFromAccumulatedAccountId());
        Account toAccumulated = lookup(transfer.getToAccumulatedAccountId());

        JournalEntry entry = JournalEntry.builder()
                .entryNo(nextJournalNo())
                .entryDate(transfer.getTransferDate())
                .type(JournalEntryType.ADJUSTMENT)
                .status(JournalEntryStatus.POSTED)
                .reference(transfer.getTransferNo())
                .memo((reversing ? "Reversal of asset transfer — " : "Asset transfer — ")
                        + transfer.getTransferNo() + " " + transfer.getAssetNo()
                        + " " + transfer.getAssetName())
                .createdBy(username)
                .build();

        int sort = 0;
        BigDecimal cost = zero(transfer.getCostAtTransfer());
        if (cost.signum() > 0 && fromAsset != null && toAsset != null
                && !Objects.equals(fromAsset.getId(), toAsset.getId())) {
            entry.addLine(line(reversing ? fromAsset : toAsset, transfer.getTransferNo(),
                    cost, BigDecimal.ZERO, sort++));
            entry.addLine(line(reversing ? toAsset : fromAsset, transfer.getTransferNo(),
                    BigDecimal.ZERO, cost, sort++));
        }

        BigDecimal accumulated = zero(transfer.getAccumulatedAtTransfer());
        if (accumulated.signum() > 0 && fromAccumulated != null && toAccumulated != null
                && !Objects.equals(fromAccumulated.getId(), toAccumulated.getId())) {
            entry.addLine(line(reversing ? toAccumulated : fromAccumulated, transfer.getTransferNo(),
                    accumulated, BigDecimal.ZERO, sort++));
            entry.addLine(line(reversing ? fromAccumulated : toAccumulated, transfer.getTransferNo(),
                    BigDecimal.ZERO, accumulated, sort++));
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

    private Account lookup(Long accountId) {
        return accountId == null ? null : accountRepository.findById(accountId).orElse(null);
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
        NumberingSequence seq = numberingSequenceRepository.findByDocType("ASSET_TRANSFER").orElse(null);
        return seq == null ? "ATR-0001" : seq.previewNext();
    }

    private String nextTransferNo() {
        NumberingSequence seq = numberingSequenceRepository.findByDocType("ASSET_TRANSFER").orElse(null);
        if (seq != null) {
            long next = seq.getNextNumber();
            String prefix = seq.getPrefix() == null ? "ATR-" : seq.getPrefix();
            String suffix = seq.getSuffix() == null ? "" : seq.getSuffix();
            int padding = seq.getPadding() == 0 ? 4 : seq.getPadding();
            String number = prefix + String.format("%0" + padding + "d", next) + suffix;
            seq.setNextNumber(next + 1);
            numberingSequenceRepository.save(seq);
            return number;
        }
        return "ATR-" + LocalDate.now().getYear() + "-" + String.format("%05d", RANDOM.nextInt(100000));
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

    private static AssetTransferStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return AssetTransferStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
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

    public record TransferSummary(long all, long draft, long completed, long cancelled, long voided,
                                  long reclassifications) {}
}
