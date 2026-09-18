/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : StockCountService.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Physical stock counts and the variances they post to the ledger
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.StockCountForm;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class StockCountService {

    private static final String MODULE = "stock-counts";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String INVENTORY_ACCOUNT_CODE = "1301";
    private static final String COGS_ACCOUNT_CODE = "5200";
    private static final String COGS_FALLBACK_CODE = "5000";

    private final StockCountRepository countRepository;
    private final StockMovementRepository movementRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final NumberingSequenceRepository numberingSequenceRepository;
    private final CompanyRepository companyRepository;
    private final InventoryService inventoryService;
    private final AuditService auditService;

    public StockCountService(StockCountRepository countRepository,
                             StockMovementRepository movementRepository,
                             ProductRepository productRepository,
                             WarehouseRepository warehouseRepository,
                             AccountRepository accountRepository,
                             JournalEntryRepository journalEntryRepository,
                             NumberingSequenceRepository numberingSequenceRepository,
                             CompanyRepository companyRepository,
                             InventoryService inventoryService,
                             AuditService auditService) {
        this.countRepository = countRepository;
        this.movementRepository = movementRepository;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.numberingSequenceRepository = numberingSequenceRepository;
        this.companyRepository = companyRepository;
        this.inventoryService = inventoryService;
        this.auditService = auditService;
    }

    // ----- Queries -----

    @Transactional(readOnly = true)
    public Page<StockCount> list(String q, String status, Long warehouseId, Pageable pageable) {
        return countRepository.search(trimToNull(q), parseStatus(status), warehouseId, pageable);
    }

    @Transactional(readOnly = true)
    public StockCount get(Long id) {
        return id == null ? null : countRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public String baseCurrency() {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElse(null);
        return company == null || company.getCurrencyCode() == null ? "RWF" : company.getCurrencyCode();
    }

    @Transactional(readOnly = true)
    public CountSummary summary() {
        return new CountSummary(
                countRepository.count(),
                countRepository.countByStatus(StockCountStatus.DRAFT),
                countRepository.countByStatus(StockCountStatus.COMPLETED),
                countRepository.countByStatus(StockCountStatus.CANCELLED),
                countRepository.countByStatus(StockCountStatus.VOID),
                zero(countRepository.totalSurplusValue()),
                zero(countRepository.totalShortageValue()));
    }

    @Transactional(readOnly = true)
    public List<StockMovement> movementsFor(StockCount count) {
        if (count == null || count.getCountNo() == null) {
            return List.of();
        }
        List<StockMovement> rows = new ArrayList<>();
        for (StockMovement m : movementRepository.findByReferenceOrderByIdAsc(count.getCountNo())) {
            if (m.getMovementType() == MovementType.ADJUSTMENT_IN
                    || m.getMovementType() == MovementType.ADJUSTMENT_OUT) {
                rows.add(m);
            }
        }
        return rows;
    }

    /**
     * A blank sheet for a location: every stocked item, with what the books currently say. The
     * expected figures are a snapshot — the variance is measured again when the count is posted.
     */
    @Transactional(readOnly = true)
    public StockCountForm blankSheet(Long warehouseId) {
        StockCountForm form = StockCountForm.empty();
        Warehouse warehouse = warehouseId == null
                ? warehouseRepository.findFirstByDefaultLocationTrue().orElse(null)
                : warehouseRepository.findById(warehouseId).orElse(null);
        if (warehouse == null) {
            return form;
        }
        form.setWarehouseId(warehouse.getId());
        Map<Long, BigDecimal> onHand = inventoryService.stockAt(warehouse.getId());
        int i = 0;
        for (Product product : productRepository.findByTrackStockTrueAndActiveTrueOrderByNameAsc()) {
            StockCountForm.Line line = form.getLines().get(i++);
            line.setProductId(product.getId());
            line.setProductSku(product.getSku());
            line.setProductName(product.getName());
            line.setUnit(product.getUnit());
            line.setExpectedQuantity(onHand.getOrDefault(product.getId(), BigDecimal.ZERO));
            line.setUnitCost(zero(product.getCostPrice()));
        }
        return form;
    }

    // ----- Create / update -----

    @Transactional
    public StockCount save(StockCountForm form, Long id, String username) {
        StockCount count;
        if (id == null) {
            count = new StockCount();
            count.setCountNo(nextCountNo());
            count.setCreatedBy(username);
            count.setStatus(StockCountStatus.DRAFT);
        } else {
            count = countRepository.findById(id).orElseThrow();
            if (!count.isEditable()) {
                throw new IllegalStateException("A completed count can no longer be edited");
            }
            count.getLines().clear();
        }

        Warehouse warehouse = warehouseRepository.findById(form.getWarehouseId()).orElseThrow();
        count.setWarehouseId(warehouse.getId());
        count.setWarehouseName(warehouse.getName());
        count.setCountDate(form.getCountDate() == null ? LocalDate.now() : form.getCountDate());
        count.setCountedBy(trimToNull(form.getCountedBy()));
        count.setReference(trimToNull(form.getReference()));
        count.setNotes(trimToNull(form.getNotes()));

        int sort = 0;
        // One line per product: a repeated product would be adjusted twice against one book figure.
        Set<Long> seen = new HashSet<>();
        for (StockCountForm.Line lineForm : form.countedLines()) {
            Product product = productRepository.findById(lineForm.getProductId()).orElse(null);
            if (product == null || !seen.add(product.getId())) {
                continue;
            }
            if (!product.isTrackStock()) {
                throw new IllegalStateException(product.getSku() + " does not carry stock");
            }
            if (lineForm.getCountedQuantity().signum() < 0) {
                throw new IllegalArgumentException(
                        "A counted quantity cannot be negative on " + product.getSku());
            }
            count.addLine(StockCountLine.builder()
                    .productId(product.getId())
                    .productSku(product.getSku())
                    .productName(product.getName())
                    .unit(product.getUnit())
                    .expectedQuantity(lineForm.expectedValue())
                    .countedQuantity(lineForm.getCountedQuantity())
                    .unitCost(lineForm.unitCostValue().signum() > 0
                            ? lineForm.unitCostValue() : zero(product.getCostPrice()))
                    .sortOrder(sort++)
                    .build());
        }

        recalculate(count);
        StockCount saved = countRepository.save(count);
        auditService.log(MODULE, id == null ? "CREATE_COUNT" : "UPDATE_COUNT",
                "stockCount#" + saved.getId(),
                saved.getCountNo() + " at " + saved.getWarehouseName());

        if (form.isCompleteNow() && saved.getStatus() == StockCountStatus.DRAFT) {
            saved = complete(saved.getId(), username);
        }
        return saved;
    }

    private void recalculate(StockCount count) {
        BigDecimal surplus = BigDecimal.ZERO;
        BigDecimal shortage = BigDecimal.ZERO;
        for (StockCountLine l : count.getLines()) {
            BigDecimal value = l.getVarianceValue();
            if (value.signum() > 0) {
                surplus = surplus.add(value);
            } else if (value.signum() < 0) {
                shortage = shortage.add(value.negate());
            }
        }
        count.setLinesCounted(count.countedLineCount());
        count.setSurplusValue(surplus);
        count.setShortageValue(shortage);
    }

    // ----- Lifecycle -----

    /**
     * Forces the books to match what was physically found. The variance is measured against stock on
     * hand at this moment, not against the snapshot taken when the sheet was opened, so anything
     * sold or received while the count was under way is not silently undone. Where the two differ
     * the line is flagged as drifted.
     */
    @Transactional
    public StockCount complete(Long id, String username) {
        StockCount count = countRepository.findById(id).orElseThrow();
        if (count.getStatus() != StockCountStatus.DRAFT) {
            throw new IllegalStateException("Only a draft count can be completed");
        }
        if (count.getLines().isEmpty()) {
            throw new IllegalStateException("Nothing was counted on this sheet");
        }

        Map<Long, Map<Long, BigDecimal>> onHand = inventoryService.onHandByWarehouse(LocalDate.now());
        LocalDate date = count.getCountDate();

        for (StockCountLine line : count.getLines()) {
            BigDecimal book = onHand.getOrDefault(line.getProductId(), Map.of())
                    .getOrDefault(count.getWarehouseId(), BigDecimal.ZERO);
            line.setBookQuantity(book);

            BigDecimal variance = line.getVariance();
            if (variance.signum() == 0) {
                continue;
            }
            boolean over = variance.signum() > 0;
            movementRepository.save(StockMovement.builder()
                    .productId(line.getProductId())
                    .productSku(line.getProductSku())
                    .productName(line.getProductName())
                    .warehouseId(count.getWarehouseId())
                    .warehouseName(count.getWarehouseName())
                    .movementType(over ? MovementType.ADJUSTMENT_IN : MovementType.ADJUSTMENT_OUT)
                    .quantity(variance.abs())
                    .unitCost(zero(line.getUnitCost()))
                    .movementDate(date)
                    .reference(count.getCountNo())
                    .notes("Stock count " + count.getCountNo() + " at " + count.getWarehouseName())
                    .createdBy(username)
                    .build());
        }

        recalculate(count);
        count.setJournalEntryId(postVariance(count, date, username, false));
        count.setStatus(StockCountStatus.COMPLETED);
        count.setCompletedAt(LocalDateTime.now());

        StockCount saved = countRepository.save(count);
        auditService.log(MODULE, "COMPLETE_COUNT", "stockCount#" + id,
                saved.getCountNo() + " — " + saved.getVarianceLineCount() + " variance(s)");
        return saved;
    }

    /**
     * Puts the stock back the way the books had it and reverses the variance entry. The original
     * movements and journal entry are left exactly as they were.
     */
    @Transactional
    public StockCount voidCount(Long id, String reason, String username) {
        StockCount count = countRepository.findById(id).orElseThrow();
        if (count.getStatus() != StockCountStatus.COMPLETED) {
            throw new IllegalStateException("Only a completed count can be voided");
        }

        LocalDate date = LocalDate.now();
        for (StockCountLine line : count.getLines()) {
            BigDecimal variance = line.getVariance();
            if (variance.signum() == 0) {
                continue;
            }
            boolean wasOver = variance.signum() > 0;
            movementRepository.save(StockMovement.builder()
                    .productId(line.getProductId())
                    .productSku(line.getProductSku())
                    .productName(line.getProductName())
                    .warehouseId(count.getWarehouseId())
                    .warehouseName(count.getWarehouseName())
                    .movementType(wasOver ? MovementType.ADJUSTMENT_OUT : MovementType.ADJUSTMENT_IN)
                    .quantity(variance.abs())
                    .unitCost(zero(line.getUnitCost()))
                    .movementDate(date)
                    .reference(count.getCountNo())
                    .notes("Void of stock count " + count.getCountNo())
                    .createdBy(username)
                    .build());
        }

        count.setReversalJournalEntryId(postVariance(count, date, username, true));
        count.setStatus(StockCountStatus.VOID);
        count.setStoppedReason(trimToNull(reason));

        StockCount saved = countRepository.save(count);
        auditService.log(MODULE, "VOID_COUNT", "stockCount#" + id,
                saved.getCountNo() + (reason == null || reason.isBlank() ? "" : " — " + reason.trim()));
        return saved;
    }

    @Transactional
    public StockCount cancel(Long id, String reason) {
        StockCount count = countRepository.findById(id).orElseThrow();
        if (count.getStatus() != StockCountStatus.DRAFT) {
            throw new IllegalStateException("Only a draft count can be cancelled");
        }
        count.setStatus(StockCountStatus.CANCELLED);
        count.setStoppedReason(trimToNull(reason));
        StockCount saved = countRepository.save(count);
        auditService.log(MODULE, "CANCEL_COUNT", "stockCount#" + id, saved.getCountNo());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        StockCount count = countRepository.findById(id).orElse(null);
        if (count == null) {
            return;
        }
        if (count.getStatus() == StockCountStatus.COMPLETED
                || count.getStatus() == StockCountStatus.VOID) {
            throw new IllegalStateException("A count that has moved stock cannot be deleted");
        }
        countRepository.delete(count);
        auditService.log(MODULE, "DELETE_COUNT", "stockCount#" + id, count.getCountNo());
    }

    // ----- Posting -----

    /**
     * One entry for the whole count, gross rather than netted, so a sheet with both surpluses and
     * shortages shows each side. Rounding drift lands on the last line so debits equal credits.
     */
    private Long postVariance(StockCount count, LocalDate date, String username, boolean reversing) {
        BigDecimal surplus = zero(count.getSurplusValue());
        BigDecimal shortage = zero(count.getShortageValue());
        if (surplus.signum() == 0 && shortage.signum() == 0) {
            return null;
        }
        Account inventory = accountRepository.findByCodeIgnoreCase(INVENTORY_ACCOUNT_CODE).orElse(null);
        Account cogs = accountRepository.findByCodeIgnoreCase(COGS_ACCOUNT_CODE)
                .or(() -> accountRepository.findByCodeIgnoreCase(COGS_FALLBACK_CODE))
                .orElse(null);
        if (inventory == null || cogs == null) {
            return null;
        }

        JournalEntry entry = JournalEntry.builder()
                .entryNo(nextJournalNo())
                .entryDate(date)
                .type(JournalEntryType.ADJUSTMENT)
                .status(JournalEntryStatus.POSTED)
                .reference(count.getCountNo())
                .memo((reversing ? "Reversal of stock count variance — " : "Stock count variance — ")
                        + count.getCountNo() + " at " + count.getWarehouseName())
                .createdBy(username)
                .build();

        int sort = 0;
        // Found more than the books said: stock goes up, cost of sales comes down.
        if (surplus.signum() > 0) {
            Account debit = reversing ? cogs : inventory;
            Account credit = reversing ? inventory : cogs;
            entry.addLine(line(debit, count.getCountNo(), surplus, BigDecimal.ZERO, sort++));
            entry.addLine(line(credit, count.getCountNo(), BigDecimal.ZERO, surplus, sort++));
        }
        // Found less: stock comes down, cost of sales goes up.
        if (shortage.signum() > 0) {
            Account debit = reversing ? inventory : cogs;
            Account credit = reversing ? cogs : inventory;
            entry.addLine(line(debit, count.getCountNo(), shortage, BigDecimal.ZERO, sort++));
            entry.addLine(line(credit, count.getCountNo(), BigDecimal.ZERO, shortage, sort++));
        }

        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        for (JournalLine l : entry.getLines()) {
            debits = debits.add(zero(l.getDebit()));
            credits = credits.add(zero(l.getCredit()));
        }
        BigDecimal drift = debits.subtract(credits);
        if (drift.signum() != 0 && !entry.getLines().isEmpty()) {
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
        NumberingSequence seq = numberingSequenceRepository.findByDocType("STOCK_COUNT").orElse(null);
        return seq == null ? "SC-0001" : seq.previewNext();
    }

    private String nextCountNo() {
        NumberingSequence seq = numberingSequenceRepository.findByDocType("STOCK_COUNT").orElse(null);
        if (seq != null) {
            long next = seq.getNextNumber();
            String prefix = seq.getPrefix() == null ? "SC-" : seq.getPrefix();
            String suffix = seq.getSuffix() == null ? "" : seq.getSuffix();
            int padding = seq.getPadding() == 0 ? 4 : seq.getPadding();
            String number = prefix + String.format("%0" + padding + "d", next) + suffix;
            seq.setNextNumber(next + 1);
            numberingSequenceRepository.save(seq);
            return number;
        }
        return "SC-" + LocalDate.now().getYear() + "-" + String.format("%05d", RANDOM.nextInt(100000));
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

    private static StockCountStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return StockCountStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
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

    public record CountSummary(long all, long draft, long completed, long cancelled, long voided,
                               BigDecimal surplusValue, BigDecimal shortageValue) {
        public BigDecimal netValue() {
            return surplusValue.subtract(shortageValue);
        }
    }
}
