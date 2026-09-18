/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : StockTransferService.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Moving stock between locations, and writing off what never arrives
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.StockTransferForm;
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
public class StockTransferService {

    private static final String MODULE = "stock-transfers";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String INVENTORY_ACCOUNT_CODE = "1301";
    private static final String COGS_ACCOUNT_CODE = "5200";
    private static final String COGS_FALLBACK_CODE = "5000";

    private final StockTransferRepository transferRepository;
    private final StockMovementRepository movementRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final NumberingSequenceRepository numberingSequenceRepository;
    private final CompanyRepository companyRepository;
    private final AuditService auditService;

    public StockTransferService(StockTransferRepository transferRepository,
                                StockMovementRepository movementRepository,
                                ProductRepository productRepository,
                                WarehouseRepository warehouseRepository,
                                AccountRepository accountRepository,
                                JournalEntryRepository journalEntryRepository,
                                NumberingSequenceRepository numberingSequenceRepository,
                                CompanyRepository companyRepository,
                                AuditService auditService) {
        this.transferRepository = transferRepository;
        this.movementRepository = movementRepository;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.numberingSequenceRepository = numberingSequenceRepository;
        this.companyRepository = companyRepository;
        this.auditService = auditService;
    }

    // ----- Queries -----

    @Transactional(readOnly = true)
    public Page<StockTransfer> list(String q, String status, Long warehouseId, Pageable pageable) {
        return transferRepository.search(trimToNull(q), parseStatus(status), warehouseId, pageable);
    }

    @Transactional(readOnly = true)
    public StockTransfer get(Long id) {
        return id == null ? null : transferRepository.findById(id).orElse(null);
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
                transferRepository.countByStatus(TransferStatus.DRAFT),
                transferRepository.countByStatus(TransferStatus.COMPLETED),
                transferRepository.countByStatus(TransferStatus.CANCELLED),
                transferRepository.countByStatus(TransferStatus.VOID),
                transferRepository.countShort(),
                zero(transferRepository.valueFor(TransferStatus.COMPLETED)),
                zero(transferRepository.totalShortfallValue()));
    }

    /**
     * Stock on hand per product and location. Movements recorded without a location — every invoice,
     * bill and credit note line writes one — are counted at the default warehouse, because that is
     * the only place they could sensibly be. Without this, stock bought on a bill would be invisible
     * to every location and no transfer could ever be raised against it.
     */
    @Transactional(readOnly = true)
    public Map<Long, Map<Long, BigDecimal>> onHandByWarehouse(LocalDate asOf) {
        Warehouse fallback = warehouseRepository.findFirstByDefaultLocationTrue().orElse(null);
        Long fallbackId = fallback == null ? null : fallback.getId();
        Map<Long, Map<Long, BigDecimal>> byProduct = new LinkedHashMap<>();
        for (Object[] row : movementRepository.onHandByProductAndWarehouse(
                asOf == null ? LocalDate.now() : asOf)) {
            Long productId = ((Number) row[0]).longValue();
            // Boxed on both branches: an unboxed long here would force fallbackId open and NPE
            // when no default warehouse is configured.
            Long warehouseId = row[1] == null
                    ? fallbackId : Long.valueOf(((Number) row[1]).longValue());
            if (warehouseId == null) {
                continue;
            }
            BigDecimal qty = row[2] == null ? BigDecimal.ZERO : (BigDecimal) row[2];
            byProduct.computeIfAbsent(productId, k -> new LinkedHashMap<>())
                    .merge(warehouseId, qty, BigDecimal::add);
        }
        return byProduct;
    }

    @Transactional(readOnly = true)
    public BigDecimal availableAt(Long productId, Long warehouseId) {
        if (productId == null || warehouseId == null) {
            return BigDecimal.ZERO;
        }
        return onHandByWarehouse(LocalDate.now())
                .getOrDefault(productId, Map.of())
                .getOrDefault(warehouseId, BigDecimal.ZERO);
    }

    /** Per-location stock for one warehouse, for the picker on the transfer form. */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> stockAt(Long warehouseId) {
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        if (warehouseId == null) {
            return result;
        }
        for (Map.Entry<Long, Map<Long, BigDecimal>> e : onHandByWarehouse(LocalDate.now()).entrySet()) {
            BigDecimal qty = e.getValue().get(warehouseId);
            if (qty != null) {
                result.put(e.getKey(), qty);
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<StockMovement> movementsFor(StockTransfer transfer) {
        if (transfer == null || transfer.getTransferNo() == null) {
            return List.of();
        }
        List<StockMovement> rows = new ArrayList<>();
        for (StockMovement m : movementRepository.findByReferenceOrderByIdAsc(transfer.getTransferNo())) {
            if (m.getMovementType() == MovementType.TRANSFER_IN
                    || m.getMovementType() == MovementType.TRANSFER_OUT) {
                rows.add(m);
            }
        }
        return rows;
    }

    // ----- Create / update -----

    @Transactional
    public StockTransfer save(StockTransferForm form, Long id, String username) {
        StockTransfer transfer;
        if (id == null) {
            transfer = new StockTransfer();
            transfer.setTransferNo(nextTransferNo());
            transfer.setCreatedBy(username);
            transfer.setStatus(TransferStatus.DRAFT);
        } else {
            transfer = transferRepository.findById(id).orElseThrow();
            if (!transfer.isEditable()) {
                throw new IllegalStateException("A completed transfer can no longer be edited");
            }
            transfer.getLines().clear();
        }

        Warehouse from = warehouseRepository.findById(form.getFromWarehouseId()).orElseThrow();
        Warehouse to = warehouseRepository.findById(form.getToWarehouseId()).orElseThrow();
        transfer.setFromWarehouseId(from.getId());
        transfer.setFromWarehouseName(from.getName());
        transfer.setToWarehouseId(to.getId());
        transfer.setToWarehouseName(to.getName());
        transfer.setTransferDate(form.getTransferDate() == null ? LocalDate.now() : form.getTransferDate());
        transfer.setReference(trimToNull(form.getReference()));
        transfer.setNotes(trimToNull(form.getNotes()));

        int sort = 0;
        for (StockTransferForm.Line lineForm : form.filledLines()) {
            Product product = productRepository.findById(lineForm.getProductId()).orElse(null);
            if (product == null) {
                continue;
            }
            if (!product.isTrackStock()) {
                throw new IllegalStateException(product.getSku() + " does not carry stock");
            }
            BigDecimal sent = lineForm.sentValue();
            BigDecimal received = lineForm.receivedValue();
            if (received.compareTo(sent) > 0) {
                throw new IllegalArgumentException(
                        "More cannot arrive than was sent on " + product.getSku());
            }
            BigDecimal unitCost = lineForm.unitCostValue().signum() > 0
                    ? lineForm.unitCostValue() : zero(product.getCostPrice());
            transfer.addLine(StockTransferLine.builder()
                    .productId(product.getId())
                    .productSku(product.getSku())
                    .productName(product.getName())
                    .unit(product.getUnit())
                    .quantitySent(sent)
                    .quantityReceived(received)
                    .unitCost(unitCost)
                    .sortOrder(sort++)
                    .build());
        }

        recalculate(transfer);
        StockTransfer saved = transferRepository.save(transfer);
        auditService.log(MODULE, id == null ? "CREATE_TRANSFER" : "UPDATE_TRANSFER",
                "stockTransfer#" + saved.getId(),
                saved.getTransferNo() + " " + saved.getFromWarehouseName()
                        + " → " + saved.getToWarehouseName());

        if (form.isCompleteNow() && saved.getStatus() == TransferStatus.DRAFT) {
            saved = complete(saved.getId(), username);
        }
        return saved;
    }

    private void recalculate(StockTransfer transfer) {
        BigDecimal sent = BigDecimal.ZERO;
        BigDecimal received = BigDecimal.ZERO;
        BigDecimal value = BigDecimal.ZERO;
        BigDecimal shortfall = BigDecimal.ZERO;
        for (StockTransferLine l : transfer.getLines()) {
            sent = sent.add(zero(l.getQuantitySent()));
            received = received.add(zero(l.getQuantityReceived()));
            value = value.add(l.getLineValue());
            shortfall = shortfall.add(l.getShortfallValue());
        }
        transfer.setTotalSent(sent);
        transfer.setTotalReceived(received);
        transfer.setTotalValue(value);
        transfer.setShortfallValue(shortfall);
    }

    // ----- Lifecycle -----

    /**
     * Moves the stock. The transfer itself never reaches the ledger — both locations feed the same
     * inventory account, so the move nets to nothing. Only stock that failed to arrive is posted,
     * as a write-off from inventory to cost of sales.
     */
    @Transactional
    public StockTransfer complete(Long id, String username) {
        StockTransfer transfer = transferRepository.findById(id).orElseThrow();
        if (transfer.getStatus() != TransferStatus.DRAFT) {
            throw new IllegalStateException("Only a draft transfer can be completed");
        }
        if (transfer.getLines().isEmpty()) {
            throw new IllegalStateException("A transfer with no line items cannot be completed");
        }
        if (transfer.getFromWarehouseId().equals(transfer.getToWarehouseId())) {
            throw new IllegalStateException("Choose two different locations");
        }

        Map<Long, Map<Long, BigDecimal>> onHand = onHandByWarehouse(LocalDate.now());
        for (StockTransferLine line : transfer.getLines()) {
            BigDecimal available = onHand.getOrDefault(line.getProductId(), Map.of())
                    .getOrDefault(transfer.getFromWarehouseId(), BigDecimal.ZERO);
            if (zero(line.getQuantitySent()).compareTo(available) > 0) {
                throw new IllegalArgumentException("Only " + available.toPlainString() + " "
                        + line.getProductSku() + " at " + transfer.getFromWarehouseName());
            }
        }

        LocalDate date = transfer.getTransferDate();
        for (StockTransferLine line : transfer.getLines()) {
            movementRepository.save(StockMovement.builder()
                    .productId(line.getProductId())
                    .productSku(line.getProductSku())
                    .productName(line.getProductName())
                    .warehouseId(transfer.getFromWarehouseId())
                    .warehouseName(transfer.getFromWarehouseName())
                    .movementType(MovementType.TRANSFER_OUT)
                    .quantity(zero(line.getQuantitySent()))
                    .unitCost(zero(line.getUnitCost()))
                    .movementDate(date)
                    .reference(transfer.getTransferNo())
                    .notes(transfer.getFromWarehouseName() + " → " + transfer.getToWarehouseName())
                    .createdBy(username)
                    .build());
            if (zero(line.getQuantityReceived()).signum() > 0) {
                movementRepository.save(StockMovement.builder()
                        .productId(line.getProductId())
                        .productSku(line.getProductSku())
                        .productName(line.getProductName())
                        .warehouseId(transfer.getToWarehouseId())
                        .warehouseName(transfer.getToWarehouseName())
                        .movementType(MovementType.TRANSFER_IN)
                        .quantity(zero(line.getQuantityReceived()))
                        .unitCost(zero(line.getUnitCost()))
                        .movementDate(date)
                        .reference(transfer.getTransferNo())
                        .notes(transfer.getFromWarehouseName() + " → " + transfer.getToWarehouseName())
                        .createdBy(username)
                        .build());
            }
        }

        recalculate(transfer);
        if (transfer.getShortfallValue().signum() > 0) {
            transfer.setJournalEntryId(postShortfall(transfer, date, username, false));
        }

        transfer.setStatus(TransferStatus.COMPLETED);
        transfer.setCompletedAt(LocalDateTime.now());
        StockTransfer saved = transferRepository.save(transfer);
        auditService.log(MODULE, "COMPLETE_TRANSFER", "stockTransfer#" + id,
                saved.getTransferNo() + " completed");
        return saved;
    }

    /**
     * Writes the mirror movements and, where stock was written off, a reversing entry. The original
     * movements and journal entry are left exactly as they were.
     */
    @Transactional
    public StockTransfer voidTransfer(Long id, String reason, String username) {
        StockTransfer transfer = transferRepository.findById(id).orElseThrow();
        if (transfer.getStatus() != TransferStatus.COMPLETED) {
            throw new IllegalStateException("Only a completed transfer can be voided");
        }

        LocalDate date = LocalDate.now();
        for (StockTransferLine line : transfer.getLines()) {
            if (zero(line.getQuantityReceived()).signum() > 0) {
                movementRepository.save(StockMovement.builder()
                        .productId(line.getProductId())
                        .productSku(line.getProductSku())
                        .productName(line.getProductName())
                        .warehouseId(transfer.getToWarehouseId())
                        .warehouseName(transfer.getToWarehouseName())
                        .movementType(MovementType.TRANSFER_OUT)
                        .quantity(zero(line.getQuantityReceived()))
                        .unitCost(zero(line.getUnitCost()))
                        .movementDate(date)
                        .reference(transfer.getTransferNo())
                        .notes("Void of " + transfer.getTransferNo())
                        .createdBy(username)
                        .build());
            }
            movementRepository.save(StockMovement.builder()
                    .productId(line.getProductId())
                    .productSku(line.getProductSku())
                    .productName(line.getProductName())
                    .warehouseId(transfer.getFromWarehouseId())
                    .warehouseName(transfer.getFromWarehouseName())
                    .movementType(MovementType.TRANSFER_IN)
                    .quantity(zero(line.getQuantitySent()))
                    .unitCost(zero(line.getUnitCost()))
                    .movementDate(date)
                    .reference(transfer.getTransferNo())
                    .notes("Void of " + transfer.getTransferNo())
                    .createdBy(username)
                    .build());
        }

        if (zero(transfer.getShortfallValue()).signum() > 0) {
            transfer.setReversalJournalEntryId(postShortfall(transfer, date, username, true));
        }

        transfer.setStatus(TransferStatus.VOID);
        transfer.setStoppedReason(trimToNull(reason));
        StockTransfer saved = transferRepository.save(transfer);
        auditService.log(MODULE, "VOID_TRANSFER", "stockTransfer#" + id,
                saved.getTransferNo() + (reason == null || reason.isBlank() ? "" : " — " + reason.trim()));
        return saved;
    }

    @Transactional
    public StockTransfer cancel(Long id, String reason) {
        StockTransfer transfer = transferRepository.findById(id).orElseThrow();
        if (transfer.getStatus() != TransferStatus.DRAFT) {
            throw new IllegalStateException("Only a draft transfer can be cancelled");
        }
        transfer.setStatus(TransferStatus.CANCELLED);
        transfer.setStoppedReason(trimToNull(reason));
        StockTransfer saved = transferRepository.save(transfer);
        auditService.log(MODULE, "CANCEL_TRANSFER", "stockTransfer#" + id, saved.getTransferNo());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        StockTransfer transfer = transferRepository.findById(id).orElse(null);
        if (transfer == null) {
            return;
        }
        if (transfer.getStatus() == TransferStatus.COMPLETED
                || transfer.getStatus() == TransferStatus.VOID) {
            throw new IllegalStateException("A transfer that has moved stock cannot be deleted");
        }
        transferRepository.delete(transfer);
        auditService.log(MODULE, "DELETE_TRANSFER", "stockTransfer#" + id, transfer.getTransferNo());
    }

    // ----- Posting -----

    private Long postShortfall(StockTransfer transfer, LocalDate date, String username,
                               boolean reversing) {
        BigDecimal value = zero(transfer.getShortfallValue());
        Account inventory = accountRepository.findByCodeIgnoreCase(INVENTORY_ACCOUNT_CODE).orElse(null);
        Account cogs = accountRepository.findByCodeIgnoreCase(COGS_ACCOUNT_CODE)
                .or(() -> accountRepository.findByCodeIgnoreCase(COGS_FALLBACK_CODE))
                .orElse(null);
        if (value.signum() == 0 || inventory == null || cogs == null) {
            return null;
        }

        Account debit = reversing ? inventory : cogs;
        Account credit = reversing ? cogs : inventory;
        String memo = (reversing ? "Reversal of stock lost in transit — " : "Stock lost in transit — ")
                + transfer.getTransferNo();

        JournalEntry entry = JournalEntry.builder()
                .entryNo(nextJournalNo())
                .entryDate(date)
                .type(JournalEntryType.ADJUSTMENT)
                .status(JournalEntryStatus.POSTED)
                .reference(transfer.getTransferNo())
                .memo(memo)
                .createdBy(username)
                .build();
        entry.addLine(JournalLine.builder()
                .accountId(debit.getId())
                .accountCode(debit.getCode())
                .accountName(debit.getName())
                .memo(transfer.getTransferNo())
                .debit(value)
                .credit(BigDecimal.ZERO)
                .sortOrder(0)
                .build());
        entry.addLine(JournalLine.builder()
                .accountId(credit.getId())
                .accountCode(credit.getCode())
                .accountName(credit.getName())
                .memo(transfer.getTransferNo())
                .debit(BigDecimal.ZERO)
                .credit(value)
                .sortOrder(1)
                .build());
        entry.setTotalDebits(value);
        entry.setTotalCredits(value);
        return journalEntryRepository.save(entry).getId();
    }

    // ----- Helpers -----

    @Transactional(readOnly = true)
    public String previewNextNumber() {
        NumberingSequence seq = numberingSequenceRepository.findByDocType("TRANSFER").orElse(null);
        return seq == null ? "TRF-0001" : seq.previewNext();
    }

    private String nextTransferNo() {
        NumberingSequence seq = numberingSequenceRepository.findByDocType("TRANSFER").orElse(null);
        if (seq != null) {
            long next = seq.getNextNumber();
            String prefix = seq.getPrefix() == null ? "TRF-" : seq.getPrefix();
            String suffix = seq.getSuffix() == null ? "" : seq.getSuffix();
            int padding = seq.getPadding() == 0 ? 4 : seq.getPadding();
            String number = prefix + String.format("%0" + padding + "d", next) + suffix;
            seq.setNextNumber(next + 1);
            numberingSequenceRepository.save(seq);
            return number;
        }
        return "TRF-" + LocalDate.now().getYear() + "-" + String.format("%05d", RANDOM.nextInt(100000));
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

    private static TransferStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return TransferStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
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
                                  long shortCount, BigDecimal movedValue, BigDecimal shortfallValue) {}
}
