/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : InventoryService.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Products, stock levels, movements and valuation
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.CategoryForm;
import com.ntaganira.heritier.ibook.dto.ProductForm;
import com.ntaganira.heritier.ibook.dto.StockAdjustmentForm;
import com.ntaganira.heritier.ibook.dto.WarehouseForm;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class InventoryService {

    private static final String MODULE = "inventory";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String INVENTORY_ACCOUNT_CODE = "1301";
    private static final String COGS_ACCOUNT_CODE = "5200";
    private static final String COGS_FALLBACK_CODE = "5000";

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final WarehouseRepository warehouseRepository;
    private final BrandRepository brandRepository;
    private final StockMovementRepository movementRepository;
    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final NumberingSequenceRepository numberingSequenceRepository;
    private final AuditService auditService;

    public InventoryService(ProductRepository productRepository,
                            ProductCategoryRepository categoryRepository,
                            WarehouseRepository warehouseRepository,
                            BrandRepository brandRepository,
                            StockMovementRepository movementRepository,
                            AccountRepository accountRepository,
                            JournalEntryRepository journalEntryRepository,
                            NumberingSequenceRepository numberingSequenceRepository,
                            AuditService auditService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.warehouseRepository = warehouseRepository;
        this.brandRepository = brandRepository;
        this.movementRepository = movementRepository;
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.numberingSequenceRepository = numberingSequenceRepository;
        this.auditService = auditService;
    }

    private static BigDecimal zero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String trimToNull(String v) {
        if (v == null) {
            return null;
        }
        String s = v.trim();
        return s.isEmpty() ? null : s;
    }

    // ----- Products -----

    @Transactional(readOnly = true)
    public Page<Product> listProducts(String q, Long categoryId, String type, Pageable pageable) {
        ProductType pt = null;
        if (type != null && !type.isBlank()) {
            try {
                pt = ProductType.valueOf(type.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                pt = null;
            }
        }
        return productRepository.search(trimToNull(q), categoryId, pt, pageable);
    }

    @Transactional(readOnly = true)
    public Product getProduct(Long id) {
        return id == null ? null : productRepository.findById(id).orElse(null);
    }

    /** Everything active, stocked or not — invoice and bill lines can reference services too. */
    @Transactional(readOnly = true)
    public List<Product> listSellableProducts() {
        return productRepository.findByActiveTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<Product> listStockedProducts() {
        return productRepository.findByTrackStockTrueAndActiveTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public boolean skuExists(String sku, Long id) {
        if (sku == null || sku.isBlank()) {
            return false;
        }
        return id == null
                ? productRepository.existsBySkuIgnoreCase(sku.trim())
                : productRepository.existsBySkuIgnoreCaseAndIdNot(sku.trim(), id);
    }

    @Transactional
    public Product saveProduct(ProductForm form, Long id) {
        Product product = id == null ? new Product() : productRepository.findById(id).orElseThrow();
        product.setSku(form.sku().trim().toUpperCase(Locale.ROOT));
        product.setName(form.name().trim());
        product.setDescription(trimToNull(form.description()));
        ProductType type;
        try {
            type = ProductType.valueOf(form.type() == null ? "GOOD" : form.type().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            type = ProductType.GOOD;
        }
        product.setType(type);
        product.setCategoryId(form.categoryId());
        if (form.categoryId() != null) {
            ProductCategory c = categoryRepository.findById(form.categoryId()).orElse(null);
            product.setCategoryName(c == null ? null : c.getName());
        } else {
            product.setCategoryName(null);
        }
        // Clearing the brand also clears the free text, so an unlinked leftover cannot linger.
        Brand brand = form.brandId() == null ? null
                : brandRepository.findById(form.brandId()).orElse(null);
        product.setBrandId(brand == null ? null : brand.getId());
        product.setBrand(brand == null ? null : brand.getName());
        product.setUnit(form.unit() == null || form.unit().isBlank() ? "each" : form.unit().trim());
        product.setCostPrice(zero(form.costPrice()));
        product.setSellingPrice(zero(form.sellingPrice()));
        product.setTaxRateId(form.taxRateId());
        product.setReorderLevel(zero(form.reorderLevel()));
        // A service never carries stock, whatever the checkbox says.
        product.setTrackStock(type == ProductType.GOOD && form.trackStock());
        product.setActive(form.active());
        Product saved = productRepository.save(product);
        auditService.log(MODULE, id == null ? "CREATE_PRODUCT" : "UPDATE_PRODUCT",
                "product#" + saved.getId(), saved.getSku() + " " + saved.getName());
        return saved;
    }

    @Transactional
    public void toggleProduct(Long id) {
        Product p = productRepository.findById(id).orElse(null);
        if (p == null) {
            return;
        }
        p.setActive(!p.isActive());
        productRepository.save(p);
        auditService.log(MODULE, "TOGGLE_PRODUCT", "product#" + id,
                p.getSku() + (p.isActive() ? " activated" : " deactivated"));
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product p = productRepository.findById(id).orElse(null);
        if (p == null) {
            return;
        }
        if (movementRepository.countByProductId(id) > 0) {
            throw new IllegalStateException("Products with stock movements cannot be deleted");
        }
        productRepository.delete(p);
        auditService.log(MODULE, "DELETE_PRODUCT", "product#" + id, p.getSku());
    }

    // ----- Categories -----

    @Transactional(readOnly = true)
    public List<ProductCategory> listCategories() {
        return categoryRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<ProductCategory> listActiveCategories() {
        return categoryRepository.findByActiveTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public ProductCategory getCategory(Long id) {
        return id == null ? null : categoryRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean categoryCodeExists(String code, Long id) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return id == null
                ? categoryRepository.existsByCodeIgnoreCase(code.trim())
                : categoryRepository.existsByCodeIgnoreCaseAndIdNot(code.trim(), id);
    }

    @Transactional
    public ProductCategory saveCategory(CategoryForm form, Long id) {
        ProductCategory c = id == null ? new ProductCategory() : categoryRepository.findById(id).orElseThrow();
        c.setCode(form.code().trim().toUpperCase(Locale.ROOT));
        c.setName(form.name().trim());
        c.setDescription(trimToNull(form.description()));
        c.setActive(form.active());
        ProductCategory saved = categoryRepository.save(c);
        // Keep the denormalised name on products in step.
        for (Product p : productRepository.findAll()) {
            if (saved.getId().equals(p.getCategoryId())
                    && !saved.getName().equals(p.getCategoryName())) {
                p.setCategoryName(saved.getName());
                productRepository.save(p);
            }
        }
        auditService.log(MODULE, id == null ? "CREATE_CATEGORY" : "UPDATE_CATEGORY",
                "category#" + saved.getId(), saved.getCode() + " " + saved.getName());
        return saved;
    }

    @Transactional
    public void deleteCategory(Long id) {
        ProductCategory c = categoryRepository.findById(id).orElse(null);
        if (c == null) {
            return;
        }
        if (productRepository.countByCategoryId(id) > 0) {
            throw new IllegalStateException("Categories in use cannot be deleted");
        }
        categoryRepository.delete(c);
        auditService.log(MODULE, "DELETE_CATEGORY", "category#" + id, c.getCode());
    }

    @Transactional(readOnly = true)
    public long productsInCategory(Long categoryId) {
        return productRepository.countByCategoryId(categoryId);
    }

    // ----- Warehouses -----

    @Transactional(readOnly = true)
    public List<Warehouse> listWarehouses() {
        return warehouseRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<Warehouse> listActiveWarehouses() {
        return warehouseRepository.findByActiveTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Warehouse getWarehouse(Long id) {
        return id == null ? null : warehouseRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean warehouseCodeExists(String code, Long id) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return id == null
                ? warehouseRepository.existsByCodeIgnoreCase(code.trim())
                : warehouseRepository.existsByCodeIgnoreCaseAndIdNot(code.trim(), id);
    }

    @Transactional
    public Warehouse saveWarehouse(WarehouseForm form, Long id) {
        Warehouse w = id == null ? new Warehouse() : warehouseRepository.findById(id).orElseThrow();
        w.setCode(form.code().trim().toUpperCase(Locale.ROOT));
        w.setName(form.name().trim());
        w.setLocation(trimToNull(form.location()));
        w.setActive(form.active());
        w.setDefaultLocation(form.defaultLocation() && form.active());
        Warehouse saved = warehouseRepository.save(w);
        if (saved.isDefaultLocation()) {
            for (Warehouse other : warehouseRepository.findAll()) {
                if (!other.getId().equals(saved.getId()) && other.isDefaultLocation()) {
                    other.setDefaultLocation(false);
                    warehouseRepository.save(other);
                }
            }
        }
        auditService.log(MODULE, id == null ? "CREATE_WAREHOUSE" : "UPDATE_WAREHOUSE",
                "warehouse#" + saved.getId(), saved.getCode() + " " + saved.getName());
        return saved;
    }

    // ----- Stock levels -----

    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> onHandByProduct(LocalDate asOf) {
        Map<Long, BigDecimal> map = new LinkedHashMap<>();
        for (Object[] row : movementRepository.onHandByProduct(asOf == null ? LocalDate.now() : asOf)) {
            map.put(((Number) row[0]).longValue(),
                    row[1] == null ? BigDecimal.ZERO : (BigDecimal) row[1]);
        }
        return map;
    }

    @Transactional(readOnly = true)
    public BigDecimal onHand(Long productId) {
        return onHandByProduct(LocalDate.now()).getOrDefault(productId, BigDecimal.ZERO);
    }

    /**
     * Stock on hand per product and location. Movements recorded without a location — every invoice,
     * bill and credit note line writes one — are counted at the default warehouse, because that is
     * the only place they could sensibly be. Without this, stock bought on a bill would be invisible
     * to every location.
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

    /** Per-location stock for one warehouse, keyed by product. */
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
    public List<StockMovement> movementsFor(Long productId) {
        return movementRepository.findByProductIdOrderByMovementDateAscIdAsc(productId);
    }

    @Transactional(readOnly = true)
    public Page<StockMovement> listMovements(Long productId, Long warehouseId, String type,
                                             LocalDate from, LocalDate to, Pageable pageable) {
        MovementType mt = null;
        if (type != null && !type.isBlank()) {
            try {
                mt = MovementType.valueOf(type.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                mt = null;
            }
        }
        return movementRepository.search(productId, warehouseId, mt, from, to, pageable);
    }

    // ----- Valuation -----

    /**
     * Stock value at standard cost — quantity on hand multiplied by the product's cost price.
     * This is not weighted-average or FIFO costing.
     */
    @Transactional(readOnly = true)
    public ValuationReport valuation(LocalDate asOf) {
        LocalDate date = asOf == null ? LocalDate.now() : asOf;
        Map<Long, BigDecimal> onHand = onHandByProduct(date);
        List<ValuationRow> rows = new ArrayList<>();
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalRetail = BigDecimal.ZERO;
        int belowReorder = 0;

        for (Product p : productRepository.findByTrackStockTrueAndActiveTrueOrderByNameAsc()) {
            BigDecimal qty = onHand.getOrDefault(p.getId(), BigDecimal.ZERO);
            BigDecimal value = qty.multiply(zero(p.getCostPrice()));
            BigDecimal retail = qty.multiply(zero(p.getSellingPrice()));
            boolean low = zero(p.getReorderLevel()).signum() > 0
                    && qty.compareTo(zero(p.getReorderLevel())) <= 0;
            if (low) {
                belowReorder++;
            }
            rows.add(new ValuationRow(p.getId(), p.getSku(), p.getName(), p.getCategoryName(),
                    p.getUnit(), qty, zero(p.getCostPrice()), value, retail,
                    zero(p.getReorderLevel()), low));
            totalValue = totalValue.add(value);
            totalRetail = totalRetail.add(retail);
        }
        rows.sort((a, b) -> b.value().compareTo(a.value()));
        return new ValuationReport(date, rows, totalValue, totalRetail, belowReorder);
    }

    // ----- Adjustments -----

    /**
     * Records a manual stock adjustment and posts it to the ledger:
     * an increase debits inventory and credits the adjustment account, a decrease does the reverse.
     */
    @Transactional
    public StockMovement adjust(StockAdjustmentForm form, String username) {
        Product product = productRepository.findById(form.productId()).orElseThrow();
        if (!product.isTrackStock()) {
            throw new IllegalStateException("This item does not carry stock");
        }
        BigDecimal qty = zero(form.quantity()).abs();
        if (qty.signum() == 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        boolean increase = !"OUT".equalsIgnoreCase(form.direction());
        LocalDate date = form.movementDate() == null ? LocalDate.now() : form.movementDate();
        BigDecimal unitCost = form.unitCost() == null ? zero(product.getCostPrice()) : form.unitCost();

        Warehouse warehouse = form.warehouseId() != null
                ? warehouseRepository.findById(form.warehouseId()).orElse(null)
                : warehouseRepository.findFirstByDefaultLocationTrue().orElse(null);

        if (!increase) {
            BigDecimal available = onHand(product.getId());
            if (qty.compareTo(available) > 0) {
                throw new IllegalArgumentException(
                        "Only " + available.toPlainString() + " in stock");
            }
        }

        StockMovement movement = StockMovement.builder()
                .productId(product.getId())
                .productSku(product.getSku())
                .productName(product.getName())
                .warehouseId(warehouse == null ? null : warehouse.getId())
                .warehouseName(warehouse == null ? null : warehouse.getName())
                .movementType(increase ? MovementType.ADJUSTMENT_IN : MovementType.ADJUSTMENT_OUT)
                .quantity(qty)
                .unitCost(unitCost)
                .movementDate(date)
                .reference(trimToNull(form.reference()))
                .notes(trimToNull(form.notes()))
                .createdBy(username)
                .build();

        BigDecimal value = qty.multiply(unitCost);
        Account inventory = accountRepository.findByCodeIgnoreCase(INVENTORY_ACCOUNT_CODE).orElse(null);
        Account contra = accountRepository.findByCodeIgnoreCase(COGS_ACCOUNT_CODE)
                .or(() -> accountRepository.findByCodeIgnoreCase(COGS_FALLBACK_CODE))
                .orElse(null);

        if (value.signum() != 0 && inventory != null && contra != null) {
            JournalEntry entry = JournalEntry.builder()
                    .entryNo(nextJournalNo())
                    .entryDate(date)
                    .type(JournalEntryType.ADJUSTMENT)
                    .status(JournalEntryStatus.POSTED)
                    .reference(product.getSku())
                    .memo("Stock adjustment " + (increase ? "in" : "out") + " \u2014 " + product.getName())
                    .createdBy(username)
                    .build();
            entry.addLine(JournalLine.builder()
                    .accountId(increase ? inventory.getId() : contra.getId())
                    .accountCode(increase ? inventory.getCode() : contra.getCode())
                    .accountName(increase ? inventory.getName() : contra.getName())
                    .memo(product.getSku())
                    .debit(value)
                    .credit(BigDecimal.ZERO)
                    .sortOrder(0)
                    .build());
            entry.addLine(JournalLine.builder()
                    .accountId(increase ? contra.getId() : inventory.getId())
                    .accountCode(increase ? contra.getCode() : inventory.getCode())
                    .accountName(increase ? contra.getName() : inventory.getName())
                    .memo(product.getSku())
                    .debit(BigDecimal.ZERO)
                    .credit(value)
                    .sortOrder(1)
                    .build());
            entry.setTotalDebits(value);
            entry.setTotalCredits(value);
            movement.setJournalEntryId(journalEntryRepository.save(entry).getId());
        }

        StockMovement saved = movementRepository.save(movement);
        auditService.log(MODULE, "ADJUST_STOCK", "product#" + product.getId(),
                product.getSku() + " " + (increase ? "+" : "-") + qty.toPlainString());
        return saved;
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

    // ----- Summary -----

    @Transactional(readOnly = true)
    public InventorySummary summary() {
        ValuationReport v = valuation(LocalDate.now());
        return new InventorySummary(
                productRepository.count(),
                productRepository.countByActiveTrue(),
                categoryRepository.count(),
                warehouseRepository.count(),
                v.totalValue(),
                v.belowReorder());
    }

    // ----- Records -----

    public record ValuationRow(Long productId, String sku, String name, String categoryName,
                               String unit, BigDecimal quantity, BigDecimal unitCost,
                               BigDecimal value, BigDecimal retailValue,
                               BigDecimal reorderLevel, boolean belowReorder) {}

    public record ValuationReport(LocalDate asOf, List<ValuationRow> rows, BigDecimal totalValue,
                                  BigDecimal totalRetail, int belowReorder) {
        public boolean isEmpty() {
            return rows.isEmpty();
        }
    }

    public record InventorySummary(long products, long activeProducts, long categories,
                                   long warehouses, BigDecimal stockValue, int belowReorder) {}
}
