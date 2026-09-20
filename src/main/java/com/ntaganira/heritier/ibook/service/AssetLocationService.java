/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : AssetLocationService.java
 * - Date      : 2026. 09. 19.
 * - User      : Hntaganira
 * - Desc      : The places fixed assets are kept, and folding in the free text that predates them
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.AssetLocationForm;
import com.ntaganira.heritier.ibook.entity.AssetLocation;
import com.ntaganira.heritier.ibook.entity.FixedAsset;
import com.ntaganira.heritier.ibook.repository.AssetLocationRepository;
import com.ntaganira.heritier.ibook.repository.FixedAssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Service
public class AssetLocationService {

    private static final String MODULE = "asset-locations";

    private final AssetLocationRepository locationRepository;
    private final FixedAssetRepository assetRepository;
    private final AuditService auditService;

    public AssetLocationService(AssetLocationRepository locationRepository,
                                FixedAssetRepository assetRepository,
                                AuditService auditService) {
        this.locationRepository = locationRepository;
        this.assetRepository = assetRepository;
        this.auditService = auditService;
    }

    // ----- Queries -----

    @Transactional(readOnly = true)
    public List<AssetLocation> list(String q) {
        return q == null || q.isBlank()
                ? locationRepository.findAllByOrderByNameAsc()
                : locationRepository.search(q.trim());
    }

    @Transactional(readOnly = true)
    public List<AssetLocation> listActive() {
        return locationRepository.findByActiveTrueOrderByNameAsc();
    }

    /**
     * The active list, plus whatever the asset already carries. A deactivated location would
     * otherwise drop out of the picker, and the next save of an unrelated field would unlink it.
     */
    @Transactional(readOnly = true)
    public List<AssetLocation> listForPicker(Long currentId) {
        List<AssetLocation> rows = new ArrayList<>(locationRepository.findByActiveTrueOrderByNameAsc());
        if (currentId != null && rows.stream().noneMatch(l -> currentId.equals(l.getId()))) {
            locationRepository.findById(currentId).ifPresent(rows::add);
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public AssetLocation get(Long id) {
        return id == null ? null : locationRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> assetCounts() {
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (AssetLocation location : locationRepository.findAll()) {
            counts.put(location.getId(), assetRepository.countByLocationId(location.getId()));
        }
        return counts;
    }

    /** What is kept at each location, at cost. Nothing here posts; it reads the register. */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> valueAtEach() {
        Map<Long, BigDecimal> values = new LinkedHashMap<>();
        for (AssetLocation location : locationRepository.findAll()) {
            BigDecimal total = BigDecimal.ZERO;
            for (FixedAsset asset : assetRepository.findByLocationId(location.getId())) {
                if (!asset.isRetired()) {
                    total = total.add(asset.getNetBookValue());
                }
            }
            values.put(location.getId(), total);
        }
        return values;
    }

    @Transactional(readOnly = true)
    public boolean nameExists(String name, Long id) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return id == null
                ? locationRepository.existsByNameIgnoreCase(name.trim())
                : locationRepository.existsByNameIgnoreCaseAndIdNot(name.trim(), id);
    }

    @Transactional(readOnly = true)
    public boolean codeExists(String code, Long id) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return id == null
                ? locationRepository.existsByCodeIgnoreCase(code.trim())
                : locationRepository.existsByCodeIgnoreCaseAndIdNot(code.trim(), id);
    }

    /**
     * Location names typed on assets before locations existed, with how many assets carry each.
     * Case-insensitive, so "Kigali yard" and "KIGALI YARD" come back as one name to import.
     */
    @Transactional(readOnly = true)
    public Map<String, Integer> unlinkedNames() {
        Map<String, Integer> names = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (FixedAsset asset : assetRepository.findWithUnlinkedLocation()) {
            names.merge(asset.getLocation().trim(), 1, Integer::sum);
        }
        return names;
    }

    @Transactional(readOnly = true)
    public LocationSummary summary() {
        List<AssetLocation> all = locationRepository.findAll();
        long active = all.stream().filter(AssetLocation::isActive).count();
        long inUse = 0;
        for (AssetLocation location : all) {
            if (assetRepository.countByLocationId(location.getId()) > 0) {
                inUse++;
            }
        }
        return new LocationSummary(all.size(), active, inUse, unlinkedNames().size());
    }

    // ----- Create / update -----

    @Transactional
    public AssetLocation save(AssetLocationForm form, Long id) {
        AssetLocation location = id == null
                ? new AssetLocation() : locationRepository.findById(id).orElseThrow();
        String previousName = location.getName();

        location.setName(form.name().trim());
        location.setCode(trimToNull(form.code()) == null
                ? null : form.code().trim().toUpperCase(Locale.ROOT));
        location.setDescription(trimToNull(form.description()));
        location.setSite(trimToNull(form.site()));
        location.setAddress(trimToNull(form.address()));
        location.setCity(trimToNull(form.city()));
        location.setManager(trimToNull(form.manager()));
        location.setActive(form.activeValue());

        AssetLocation saved = locationRepository.save(location);
        if (previousName != null && !previousName.equals(saved.getName())) {
            renameOnAssets(saved);
        }
        auditService.log(MODULE, id == null ? "CREATE_ASSET_LOCATION" : "UPDATE_ASSET_LOCATION",
                "assetLocation#" + saved.getId(), saved.getName());
        return saved;
    }

    /**
     * Keeps the denormalised name on assets in step. A completed transfer keeps the old name it
     * recorded, because that is what the place was called when the asset moved.
     */
    private void renameOnAssets(AssetLocation location) {
        for (FixedAsset asset : assetRepository.findByLocationId(location.getId())) {
            asset.setLocation(location.getName());
            assetRepository.save(asset);
        }
    }

    @Transactional
    public void toggle(Long id) {
        AssetLocation location = locationRepository.findById(id).orElse(null);
        if (location == null) {
            return;
        }
        location.setActive(!location.isActive());
        locationRepository.save(location);
        auditService.log(MODULE,
                location.isActive() ? "ACTIVATE_ASSET_LOCATION" : "DEACTIVATE_ASSET_LOCATION",
                "assetLocation#" + id, location.getName());
    }

    @Transactional
    public void delete(Long id) {
        AssetLocation location = locationRepository.findById(id).orElse(null);
        if (location == null) {
            return;
        }
        if (assetRepository.countByLocationId(id) > 0) {
            throw new IllegalStateException("Locations in use cannot be deleted");
        }
        locationRepository.delete(location);
        auditService.log(MODULE, "DELETE_ASSET_LOCATION", "assetLocation#" + id, location.getName());
    }

    /**
     * Turns the location names already typed on assets into records and links the assets to them.
     * The text on the asset is left exactly as it was — only the link is new.
     */
    @Transactional
    public ImportResult importFromAssets() {
        // Read the assets once: linking them removes them from the unlinked query as it goes.
        Map<String, List<FixedAsset>> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (FixedAsset asset : assetRepository.findWithUnlinkedLocation()) {
            byName.computeIfAbsent(asset.getLocation().trim(), k -> new ArrayList<>()).add(asset);
        }

        int created = 0;
        int linked = 0;
        for (Map.Entry<String, List<FixedAsset>> entry : byName.entrySet()) {
            String name = entry.getKey();
            AssetLocation location = locationRepository.findByNameIgnoreCase(name).orElse(null);
            if (location == null) {
                location = locationRepository.save(
                        AssetLocation.builder().name(name).active(true).build());
                created++;
            }
            for (FixedAsset asset : entry.getValue()) {
                asset.setLocationId(location.getId());
                asset.setLocation(location.getName());
                assetRepository.save(asset);
                linked++;
            }
        }
        if (created > 0 || linked > 0) {
            auditService.log(MODULE, "IMPORT_ASSET_LOCATIONS", "assetLocations",
                    created + " created, " + linked + " asset(s) linked");
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

    public record LocationSummary(long all, long active, long inUse, long unlinkedNames) {}
}
