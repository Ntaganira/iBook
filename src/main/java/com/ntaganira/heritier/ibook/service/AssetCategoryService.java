/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : AssetCategoryService.java
 * - Date      : 2026. 09. 19.
 * - User      : Hntaganira
 * - Desc      : Fixed asset categories, the policy they carry, and folding in the free text
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.AssetCategoryForm;
import com.ntaganira.heritier.ibook.entity.Account;
import com.ntaganira.heritier.ibook.entity.AssetCategory;
import com.ntaganira.heritier.ibook.entity.FixedAsset;
import com.ntaganira.heritier.ibook.enums.DepreciationMethod;
import com.ntaganira.heritier.ibook.repository.AccountRepository;
import com.ntaganira.heritier.ibook.repository.AssetCategoryRepository;
import com.ntaganira.heritier.ibook.repository.FixedAssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Service
public class AssetCategoryService {

    private static final String MODULE = "asset-categories";

    private final AssetCategoryRepository categoryRepository;
    private final FixedAssetRepository assetRepository;
    private final AccountRepository accountRepository;
    private final AuditService auditService;

    public AssetCategoryService(AssetCategoryRepository categoryRepository,
                                FixedAssetRepository assetRepository,
                                AccountRepository accountRepository,
                                AuditService auditService) {
        this.categoryRepository = categoryRepository;
        this.assetRepository = assetRepository;
        this.accountRepository = accountRepository;
        this.auditService = auditService;
    }

    // ----- Queries -----

    @Transactional(readOnly = true)
    public List<AssetCategory> list(String q) {
        return q == null || q.isBlank()
                ? categoryRepository.findAllByOrderByNameAsc()
                : categoryRepository.search(q.trim());
    }

    @Transactional(readOnly = true)
    public List<AssetCategory> listActive() {
        return categoryRepository.findByActiveTrueOrderByNameAsc();
    }

    /**
     * The active list, plus whatever the asset already carries. A deactivated category would
     * otherwise drop out of the picker, and the next save of an unrelated field would unlink it.
     */
    @Transactional(readOnly = true)
    public List<AssetCategory> listForPicker(Long currentId) {
        List<AssetCategory> rows = new ArrayList<>(categoryRepository.findByActiveTrueOrderByNameAsc());
        if (currentId != null && rows.stream().noneMatch(c -> currentId.equals(c.getId()))) {
            categoryRepository.findById(currentId).ifPresent(rows::add);
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public AssetCategory get(Long id) {
        return id == null ? null : categoryRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> assetCounts() {
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (AssetCategory category : categoryRepository.findAll()) {
            counts.put(category.getId(), assetRepository.countByCategoryId(category.getId()));
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public boolean nameExists(String name, Long id) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return id == null
                ? categoryRepository.existsByNameIgnoreCase(name.trim())
                : categoryRepository.existsByNameIgnoreCaseAndIdNot(name.trim(), id);
    }

    @Transactional(readOnly = true)
    public boolean codeExists(String code, Long id) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return id == null
                ? categoryRepository.existsByCodeIgnoreCase(code.trim())
                : categoryRepository.existsByCodeIgnoreCaseAndIdNot(code.trim(), id);
    }

    /**
     * Category names typed on assets before categories existed, with how many assets carry each.
     * Case-insensitive, so "Vehicles" and "vehicles" come back as one name to import.
     */
    @Transactional(readOnly = true)
    public Map<String, Integer> unlinkedNames() {
        Map<String, Integer> names = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (FixedAsset asset : assetRepository.findWithUnlinkedCategory()) {
            names.merge(asset.getCategory().trim(), 1, Integer::sum);
        }
        return names;
    }

    @Transactional(readOnly = true)
    public CategorySummary summary() {
        List<AssetCategory> all = categoryRepository.findAll();
        long active = all.stream().filter(AssetCategory::isActive).count();
        long inUse = 0;
        for (AssetCategory category : all) {
            if (assetRepository.countByCategoryId(category.getId()) > 0) {
                inUse++;
            }
        }
        return new CategorySummary(all.size(), active, inUse, unlinkedNames().size());
    }

    // ----- Create / update -----

    @Transactional
    public AssetCategory save(AssetCategoryForm form, Long id) {
        AssetCategory category = id == null
                ? new AssetCategory() : categoryRepository.findById(id).orElseThrow();
        String previousName = category.getName();

        DepreciationMethod method = parseMethod(form.depreciationMethod());
        if (method == DepreciationMethod.REDUCING_BALANCE
                && form.decliningRateOrZero().signum() <= 0) {
            throw new IllegalArgumentException("Reducing balance needs a rate above zero");
        }

        category.setName(form.name().trim());
        category.setCode(trimToNull(form.code()) == null
                ? null : form.code().trim().toUpperCase(Locale.ROOT));
        category.setDescription(trimToNull(form.description()));
        category.setDepreciationMethod(method);
        category.setUsefulLifeYears(method == DepreciationMethod.NONE ? 0 : form.usefulLifeValue());
        category.setDecliningRate(method == DepreciationMethod.REDUCING_BALANCE
                ? form.decliningRateOrZero() : java.math.BigDecimal.ZERO);
        applyAccounts(category, form);
        category.setActive(form.activeValue());

        AssetCategory saved = categoryRepository.save(category);
        if (previousName != null && !previousName.equals(saved.getName())) {
            renameOnAssets(saved);
        }
        auditService.log(MODULE, id == null ? "CREATE_ASSET_CATEGORY" : "UPDATE_ASSET_CATEGORY",
                "assetCategory#" + saved.getId(), saved.getName());
        return saved;
    }

    private void applyAccounts(AssetCategory category, AssetCategoryForm form) {
        Account assetAccount = lookup(form.assetAccountId());
        Account accumulated = lookup(form.accumulatedAccountId());
        Account expense = lookup(form.expenseAccountId());
        category.setAssetAccountId(assetAccount == null ? null : assetAccount.getId());
        category.setAssetAccountCode(assetAccount == null ? null : assetAccount.getCode());
        category.setAccumulatedAccountId(accumulated == null ? null : accumulated.getId());
        category.setAccumulatedAccountCode(accumulated == null ? null : accumulated.getCode());
        category.setExpenseAccountId(expense == null ? null : expense.getId());
        category.setExpenseAccountCode(expense == null ? null : expense.getCode());
    }

    private Account lookup(Long accountId) {
        return accountId == null ? null : accountRepository.findById(accountId).orElse(null);
    }

    /** Keeps the denormalised name on assets in step, the way brands do on products. */
    private void renameOnAssets(AssetCategory category) {
        for (FixedAsset asset : assetRepository.findByCategoryId(category.getId())) {
            asset.setCategory(category.getName());
            assetRepository.save(asset);
        }
    }

    @Transactional
    public void toggle(Long id) {
        AssetCategory category = categoryRepository.findById(id).orElse(null);
        if (category == null) {
            return;
        }
        category.setActive(!category.isActive());
        categoryRepository.save(category);
        auditService.log(MODULE,
                category.isActive() ? "ACTIVATE_ASSET_CATEGORY" : "DEACTIVATE_ASSET_CATEGORY",
                "assetCategory#" + id, category.getName());
    }

    @Transactional
    public void delete(Long id) {
        AssetCategory category = categoryRepository.findById(id).orElse(null);
        if (category == null) {
            return;
        }
        if (assetRepository.countByCategoryId(id) > 0) {
            throw new IllegalStateException("Categories in use cannot be deleted");
        }
        categoryRepository.delete(category);
        auditService.log(MODULE, "DELETE_ASSET_CATEGORY", "assetCategory#" + id, category.getName());
    }

    /**
     * Turns the category names already typed on assets into records and links the assets to them.
     * The text on the asset is left exactly as it was — only the link is new. An imported category
     * carries no policy, because nothing in free text says what the policy was.
     */
    @Transactional
    public ImportResult importFromAssets() {
        // Read the assets once: linking them removes them from the unlinked query as it goes.
        Map<String, List<FixedAsset>> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (FixedAsset asset : assetRepository.findWithUnlinkedCategory()) {
            byName.computeIfAbsent(asset.getCategory().trim(), k -> new ArrayList<>()).add(asset);
        }

        int created = 0;
        int linked = 0;
        for (Map.Entry<String, List<FixedAsset>> entry : byName.entrySet()) {
            String name = entry.getKey();
            AssetCategory category = categoryRepository.findByNameIgnoreCase(name).orElse(null);
            if (category == null) {
                category = categoryRepository.save(AssetCategory.builder()
                        .name(name)
                        .depreciationMethod(DepreciationMethod.STRAIGHT_LINE)
                        .active(true)
                        .build());
                created++;
            }
            for (FixedAsset asset : entry.getValue()) {
                asset.setCategoryId(category.getId());
                asset.setCategory(category.getName());
                assetRepository.save(asset);
                linked++;
            }
        }
        if (created > 0 || linked > 0) {
            auditService.log(MODULE, "IMPORT_ASSET_CATEGORIES", "assetCategories",
                    created + " created, " + linked + " asset(s) linked");
        }
        return new ImportResult(created, linked);
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

    public record CategorySummary(long all, long active, long inUse, long unlinkedNames) {}
}
