/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : BrandService.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Product brands, and folding in the free text that predates them
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.BrandForm;
import com.ntaganira.heritier.ibook.entity.Brand;
import com.ntaganira.heritier.ibook.entity.Product;
import com.ntaganira.heritier.ibook.repository.BrandRepository;
import com.ntaganira.heritier.ibook.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Service
public class BrandService {

    private static final String MODULE = "brands";

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final AuditService auditService;

    public BrandService(BrandRepository brandRepository,
                        ProductRepository productRepository,
                        AuditService auditService) {
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
        this.auditService = auditService;
    }

    // ----- Queries -----

    @Transactional(readOnly = true)
    public List<Brand> list(String q) {
        return q == null || q.isBlank()
                ? brandRepository.findAllByOrderByNameAsc()
                : brandRepository.search(q.trim());
    }

    @Transactional(readOnly = true)
    public List<Brand> listActive() {
        return brandRepository.findByActiveTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Brand get(Long id) {
        return id == null ? null : brandRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> productCounts() {
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (Brand brand : brandRepository.findAll()) {
            counts.put(brand.getId(), productRepository.countByBrandId(brand.getId()));
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public boolean nameExists(String name, Long id) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return id == null
                ? brandRepository.existsByNameIgnoreCase(name.trim())
                : brandRepository.existsByNameIgnoreCaseAndIdNot(name.trim(), id);
    }

    @Transactional(readOnly = true)
    public boolean codeExists(String code, Long id) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return id == null
                ? brandRepository.existsByCodeIgnoreCase(code.trim())
                : brandRepository.existsByCodeIgnoreCaseAndIdNot(code.trim(), id);
    }

    /**
     * Brand names typed on products before brands existed, with how many products carry each.
     * Case-insensitive, so "Simba" and "SIMBA" come back as one name to import.
     */
    @Transactional(readOnly = true)
    public Map<String, Integer> unlinkedBrandNames() {
        Map<String, Integer> names = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Product product : productRepository.findWithUnlinkedBrand()) {
            String name = product.getBrand().trim();
            names.merge(name, 1, Integer::sum);
        }
        return names;
    }

    @Transactional(readOnly = true)
    public BrandSummary summary() {
        List<Brand> all = brandRepository.findAll();
        long active = all.stream().filter(Brand::isActive).count();
        long linked = 0;
        for (Brand brand : all) {
            if (productRepository.countByBrandId(brand.getId()) > 0) {
                linked++;
            }
        }
        return new BrandSummary(all.size(), active, linked, unlinkedBrandNames().size());
    }

    // ----- Create / update -----

    @Transactional
    public Brand save(BrandForm form, Long id) {
        Brand brand = id == null ? new Brand() : brandRepository.findById(id).orElseThrow();
        String previousName = brand.getName();

        brand.setName(form.name().trim());
        brand.setCode(trimToNull(form.code()) == null
                ? null : form.code().trim().toUpperCase(Locale.ROOT));
        brand.setDescription(trimToNull(form.description()));
        brand.setManufacturer(trimToNull(form.manufacturer()));
        brand.setWebsite(trimToNull(form.website()));
        brand.setActive(form.activeValue());

        Brand saved = brandRepository.save(brand);
        if (previousName != null && !previousName.equals(saved.getName())) {
            renameOnProducts(saved);
        }
        auditService.log(MODULE, id == null ? "CREATE_BRAND" : "UPDATE_BRAND",
                "brand#" + saved.getId(), saved.getName());
        return saved;
    }

    /** Keeps the denormalised name on products in step, the way categories already do. */
    private void renameOnProducts(Brand brand) {
        for (Product product : productRepository.findByBrandId(brand.getId())) {
            product.setBrand(brand.getName());
            productRepository.save(product);
        }
    }

    @Transactional
    public void toggle(Long id) {
        Brand brand = brandRepository.findById(id).orElse(null);
        if (brand == null) {
            return;
        }
        brand.setActive(!brand.isActive());
        brandRepository.save(brand);
        auditService.log(MODULE, brand.isActive() ? "ACTIVATE_BRAND" : "DEACTIVATE_BRAND",
                "brand#" + id, brand.getName());
    }

    @Transactional
    public void delete(Long id) {
        Brand brand = brandRepository.findById(id).orElse(null);
        if (brand == null) {
            return;
        }
        if (productRepository.countByBrandId(id) > 0) {
            throw new IllegalStateException("Brands in use cannot be deleted");
        }
        brandRepository.delete(brand);
        auditService.log(MODULE, "DELETE_BRAND", "brand#" + id, brand.getName());
    }

    /**
     * Turns the brand names already typed on products into records and links the products to them.
     * An existing brand with the same name is reused rather than duplicated, and the text on the
     * product is left exactly as it was — only the link is new.
     */
    @Transactional
    public ImportResult importFromProducts() {
        // Read the products once: linking them removes them from the unlinked query as it goes.
        Map<String, List<Product>> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Product product : productRepository.findWithUnlinkedBrand()) {
            byName.computeIfAbsent(product.getBrand().trim(), k -> new ArrayList<>()).add(product);
        }

        int created = 0;
        int linked = 0;
        for (Map.Entry<String, List<Product>> entry : byName.entrySet()) {
            String name = entry.getKey();
            Brand brand = brandRepository.findByNameIgnoreCase(name).orElse(null);
            if (brand == null) {
                brand = brandRepository.save(Brand.builder().name(name).active(true).build());
                created++;
            }
            for (Product product : entry.getValue()) {
                product.setBrandId(brand.getId());
                product.setBrand(brand.getName());
                productRepository.save(product);
                linked++;
            }
        }
        if (created > 0 || linked > 0) {
            auditService.log(MODULE, "IMPORT_BRANDS", "brands",
                    created + " created, " + linked + " product(s) linked");
        }
        return new ImportResult(created, linked);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record ImportResult(int created, int linked) {
        public boolean isEmpty() {
            return created == 0 && linked == 0;
        }
    }

    public record BrandSummary(long all, long active, long inUse, long unlinkedNames) {}
}
