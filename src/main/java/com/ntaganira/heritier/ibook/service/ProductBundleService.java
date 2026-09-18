/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : ProductBundleService.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Bundles of products, and assembling them out of their parts
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.ProductBundleForm;
import com.ntaganira.heritier.ibook.entity.*;
import com.ntaganira.heritier.ibook.enums.MovementType;
import com.ntaganira.heritier.ibook.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ProductBundleService {

    private static final String MODULE = "bundles";

    private final ProductBundleRepository bundleRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockMovementRepository movementRepository;
    private final CompanyRepository companyRepository;
    private final InventoryService inventoryService;
    private final AuditService auditService;

    public ProductBundleService(ProductBundleRepository bundleRepository,
                                ProductRepository productRepository,
                                WarehouseRepository warehouseRepository,
                                StockMovementRepository movementRepository,
                                CompanyRepository companyRepository,
                                InventoryService inventoryService,
                                AuditService auditService) {
        this.bundleRepository = bundleRepository;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.movementRepository = movementRepository;
        this.companyRepository = companyRepository;
        this.inventoryService = inventoryService;
        this.auditService = auditService;
    }

    // ----- Queries -----

    @Transactional(readOnly = true)
    public List<ProductBundle> list(String q) {
        return q == null || q.isBlank()
                ? bundleRepository.findAllByOrderByNameAsc()
                : bundleRepository.search(q.trim());
    }

    @Transactional(readOnly = true)
    public ProductBundle get(Long id) {
        return id == null ? null : bundleRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public String baseCurrency() {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElse(null);
        return company == null || company.getCurrencyCode() == null ? "RWF" : company.getCurrencyCode();
    }

    @Transactional(readOnly = true)
    public boolean codeExists(String code, Long id) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return id == null
                ? bundleRepository.existsByCodeIgnoreCase(code.trim())
                : bundleRepository.existsByCodeIgnoreCaseAndIdNot(code.trim(), id);
    }

    @Transactional(readOnly = true)
    public boolean bundleProductTaken(Long productId, Long id) {
        if (productId == null) {
            return false;
        }
        return id == null
                ? bundleRepository.existsByBundleProductId(productId)
                : bundleRepository.existsByBundleProductIdAndIdNot(productId, id);
    }

    @Transactional(readOnly = true)
    public BundleSummary summary() {
        List<ProductBundle> all = bundleRepository.findAllByOrderByNameAsc();
        long lossMaking = all.stream().filter(ProductBundle::isLossMaking).count();
        BigDecimal value = BigDecimal.ZERO;
        for (ProductBundle b : all) {
            value = value.add(zero(b.getUnitCost()));
        }
        return new BundleSummary(all.size(), bundleRepository.countByActiveTrue(), lossMaking, value);
    }

    /**
     * Per-component stock at a location, and how many whole bundles each component alone would
     * allow. The bundle as a whole is limited by the scarcest of them.
     */
    @Transactional(readOnly = true)
    public BundleAvailability availability(ProductBundle bundle, Long warehouseId) {
        List<ComponentAvailability> rows = new ArrayList<>();
        BigDecimal buildable = null;
        Map<Long, BigDecimal> stock = inventoryService.stockAt(warehouseId);

        for (ProductBundleComponent component : bundle.getComponents()) {
            BigDecimal available = stock.getOrDefault(component.getProductId(), BigDecimal.ZERO);
            BigDecimal per = zero(component.getQuantity());
            BigDecimal possible = per.signum() <= 0 ? BigDecimal.ZERO
                    : available.divide(per, 0, RoundingMode.FLOOR);
            if (possible.signum() < 0) {
                possible = BigDecimal.ZERO;
            }
            rows.add(new ComponentAvailability(component.getProductId(), component.getProductSku(),
                    component.getProductName(), component.getUnit(), per, available, possible,
                    zero(component.getUnitCost()), component.getLineCost()));
            buildable = buildable == null || possible.compareTo(buildable) < 0 ? possible : buildable;
        }

        BigDecimal onHand = warehouseId == null ? BigDecimal.ZERO
                : stock.getOrDefault(bundle.getBundleProductId(), BigDecimal.ZERO);
        return new BundleAvailability(rows, buildable == null ? BigDecimal.ZERO : buildable, onHand);
    }

    @Transactional(readOnly = true)
    public List<StockMovement> movementsFor(ProductBundle bundle) {
        if (bundle == null || bundle.getCode() == null) {
            return List.of();
        }
        return movementRepository.findByReferenceOrderByIdAsc(bundle.getCode());
    }

    // ----- Create / update -----

    @Transactional
    public ProductBundle save(ProductBundleForm form, Long id, String username) {
        ProductBundle bundle;
        if (id == null) {
            bundle = new ProductBundle();
            bundle.setCreatedBy(username);
        } else {
            bundle = bundleRepository.findById(id).orElseThrow();
            bundle.getComponents().clear();
        }

        Product bundleProduct = productRepository.findById(form.getBundleProductId()).orElseThrow();
        if (!bundleProduct.isTrackStock()) {
            throw new IllegalStateException(bundleProduct.getSku()
                    + " does not carry stock, so it cannot be assembled into");
        }

        bundle.setCode(form.getCode().trim().toUpperCase(Locale.ROOT));
        bundle.setName(form.getName().trim());
        bundle.setDescription(trimToNull(form.getDescription()));
        bundle.setBundleProductId(bundleProduct.getId());
        bundle.setBundleProductSku(bundleProduct.getSku());
        bundle.setBundleProductName(bundleProduct.getName());
        bundle.setSellingPrice(zero(bundleProduct.getSellingPrice()));
        bundle.setActive(form.isActive());

        int sort = 0;
        Set<Long> seen = new HashSet<>();
        for (ProductBundleForm.Line line : form.filledLines()) {
            Product component = productRepository.findById(line.getProductId()).orElse(null);
            if (component == null || !seen.add(component.getId())) {
                continue;
            }
            if (component.getId().equals(bundleProduct.getId())) {
                throw new IllegalStateException("A bundle cannot contain itself");
            }
            if (!component.isTrackStock()) {
                throw new IllegalStateException(component.getSku() + " does not carry stock");
            }
            BigDecimal unitCost = line.unitCostValue().signum() > 0
                    ? line.unitCostValue() : zero(component.getCostPrice());
            bundle.addComponent(ProductBundleComponent.builder()
                    .productId(component.getId())
                    .productSku(component.getSku())
                    .productName(component.getName())
                    .unit(component.getUnit())
                    .quantity(line.quantityValue())
                    .unitCost(unitCost)
                    .sortOrder(sort++)
                    .build());
        }
        if (bundle.getComponents().isEmpty()) {
            throw new IllegalStateException("A bundle needs at least one component");
        }

        BigDecimal rolledUp = BigDecimal.ZERO;
        for (ProductBundleComponent c : bundle.getComponents()) {
            rolledUp = rolledUp.add(c.getLineCost());
        }
        bundle.setUnitCost(rolledUp.setScale(2, RoundingMode.HALF_UP));

        ProductBundle saved = bundleRepository.save(bundle);

        // Cost of sales on a bundle reads the product's cost price, so it has to be the roll-up or
        // selling an assembled bundle would post a cost the assembly never capitalised.
        if (zero(bundleProduct.getCostPrice()).compareTo(saved.getUnitCost()) != 0) {
            bundleProduct.setCostPrice(saved.getUnitCost());
            productRepository.save(bundleProduct);
        }

        auditService.log(MODULE, id == null ? "CREATE_BUNDLE" : "UPDATE_BUNDLE",
                "productBundle#" + saved.getId(), saved.getCode() + " " + saved.getName());
        return saved;
    }

    @Transactional
    public void toggle(Long id) {
        ProductBundle bundle = bundleRepository.findById(id).orElse(null);
        if (bundle == null) {
            return;
        }
        bundle.setActive(!bundle.isActive());
        bundleRepository.save(bundle);
        auditService.log(MODULE, bundle.isActive() ? "ACTIVATE_BUNDLE" : "DEACTIVATE_BUNDLE",
                "productBundle#" + id, bundle.getName());
    }

    /**
     * Removes the recipe only. Anything already assembled stays on the shelf and in the ledger —
     * deleting a bundle is not the same as taking it apart.
     */
    @Transactional
    public void delete(Long id) {
        ProductBundle bundle = bundleRepository.findById(id).orElse(null);
        if (bundle == null) {
            return;
        }
        bundleRepository.delete(bundle);
        auditService.log(MODULE, "DELETE_BUNDLE", "productBundle#" + id, bundle.getCode());
    }

    // ----- Assembly -----

    /**
     * Consumes the components and creates bundle stock in their place. Nothing reaches the ledger:
     * the bundle is capitalised at exactly the component costs that were consumed, so the value of
     * stock on hand is unchanged and a journal entry would be a no-op.
     */
    @Transactional
    public AssemblyResult assemble(Long id, Long warehouseId, BigDecimal quantity, String username) {
        ProductBundle bundle = bundleRepository.findById(id).orElseThrow();
        BigDecimal qty = requirePositive(quantity);
        Warehouse warehouse = resolveWarehouse(warehouseId);
        if (bundle.getComponents().isEmpty()) {
            throw new IllegalStateException("This bundle has no components");
        }

        Map<Long, BigDecimal> stock = inventoryService.stockAt(warehouse.getId());
        for (ProductBundleComponent component : bundle.getComponents()) {
            BigDecimal needed = zero(component.getQuantity()).multiply(qty);
            BigDecimal available = stock.getOrDefault(component.getProductId(), BigDecimal.ZERO);
            if (needed.compareTo(available) > 0) {
                throw new IllegalArgumentException("Only " + available.toPlainString() + " "
                        + component.getProductSku() + " at " + warehouse.getName()
                        + ", " + needed.toPlainString() + " needed");
            }
        }

        LocalDate today = LocalDate.now();
        for (ProductBundleComponent component : bundle.getComponents()) {
            movementRepository.save(movement(component.getProductId(), component.getProductSku(),
                    component.getProductName(), warehouse, MovementType.ADJUSTMENT_OUT,
                    zero(component.getQuantity()).multiply(qty), zero(component.getUnitCost()),
                    today, bundle.getCode(),
                    "Assembled into " + bundle.getCode(), username));
        }
        movementRepository.save(movement(bundle.getBundleProductId(), bundle.getBundleProductSku(),
                bundle.getBundleProductName(), warehouse, MovementType.ADJUSTMENT_IN,
                qty, zero(bundle.getUnitCost()), today, bundle.getCode(),
                "Assembled from components", username));

        BigDecimal value = zero(bundle.getUnitCost()).multiply(qty);
        auditService.log(MODULE, "ASSEMBLE_BUNDLE", "productBundle#" + id,
                qty.toPlainString() + " × " + bundle.getCode() + " at " + warehouse.getName());
        return new AssemblyResult(qty, value, warehouse.getName());
    }

    /** The reverse: bundle stock goes back to being its parts, again at no cost to the ledger. */
    @Transactional
    public AssemblyResult disassemble(Long id, Long warehouseId, BigDecimal quantity, String username) {
        ProductBundle bundle = bundleRepository.findById(id).orElseThrow();
        BigDecimal qty = requirePositive(quantity);
        Warehouse warehouse = resolveWarehouse(warehouseId);

        Map<Long, BigDecimal> stock = inventoryService.stockAt(warehouse.getId());
        BigDecimal onHand = stock.getOrDefault(bundle.getBundleProductId(), BigDecimal.ZERO);
        if (qty.compareTo(onHand) > 0) {
            throw new IllegalArgumentException("Only " + onHand.toPlainString() + " "
                    + bundle.getBundleProductSku() + " at " + warehouse.getName());
        }

        LocalDate today = LocalDate.now();
        movementRepository.save(movement(bundle.getBundleProductId(), bundle.getBundleProductSku(),
                bundle.getBundleProductName(), warehouse, MovementType.ADJUSTMENT_OUT,
                qty, zero(bundle.getUnitCost()), today, bundle.getCode(),
                "Taken apart into components", username));
        for (ProductBundleComponent component : bundle.getComponents()) {
            movementRepository.save(movement(component.getProductId(), component.getProductSku(),
                    component.getProductName(), warehouse, MovementType.ADJUSTMENT_IN,
                    zero(component.getQuantity()).multiply(qty), zero(component.getUnitCost()),
                    today, bundle.getCode(),
                    "Recovered from " + bundle.getCode(), username));
        }

        BigDecimal value = zero(bundle.getUnitCost()).multiply(qty);
        auditService.log(MODULE, "DISASSEMBLE_BUNDLE", "productBundle#" + id,
                qty.toPlainString() + " × " + bundle.getCode() + " at " + warehouse.getName());
        return new AssemblyResult(qty, value, warehouse.getName());
    }

    private StockMovement movement(Long productId, String sku, String name, Warehouse warehouse,
                                   MovementType type, BigDecimal quantity, BigDecimal unitCost,
                                   LocalDate date, String reference, String notes, String username) {
        return StockMovement.builder()
                .productId(productId)
                .productSku(sku)
                .productName(name)
                .warehouseId(warehouse.getId())
                .warehouseName(warehouse.getName())
                .movementType(type)
                .quantity(quantity)
                .unitCost(unitCost)
                .movementDate(date)
                .reference(reference)
                .notes(notes)
                .createdBy(username)
                .build();
    }

    private Warehouse resolveWarehouse(Long warehouseId) {
        Warehouse warehouse = warehouseId == null
                ? warehouseRepository.findFirstByDefaultLocationTrue().orElse(null)
                : warehouseRepository.findById(warehouseId).orElse(null);
        if (warehouse == null) {
            throw new IllegalStateException("Choose a location to assemble at");
        }
        return warehouse;
    }

    private static BigDecimal requirePositive(BigDecimal quantity) {
        BigDecimal qty = zero(quantity);
        if (qty.signum() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        return qty;
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

    public record ComponentAvailability(Long productId, String sku, String name, String unit,
                                        BigDecimal perBundle, BigDecimal available,
                                        BigDecimal possibleBundles, BigDecimal unitCost,
                                        BigDecimal lineCost) {
        public boolean isShort() {
            return possibleBundles.signum() == 0;
        }
    }

    public record BundleAvailability(List<ComponentAvailability> components, BigDecimal buildable,
                                     BigDecimal onHand) {
        public boolean canBuild() {
            return buildable.signum() > 0;
        }
    }

    public record AssemblyResult(BigDecimal quantity, BigDecimal value, String warehouseName) {}

    public record BundleSummary(long all, long active, long lossMaking, BigDecimal totalCost) {}
}
