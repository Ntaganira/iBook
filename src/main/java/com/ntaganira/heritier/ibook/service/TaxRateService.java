/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : TaxRateService.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Tax rate configuration service
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.TaxRateForm;
import com.ntaganira.heritier.ibook.entity.TaxRate;
import com.ntaganira.heritier.ibook.enums.TaxTreatment;
import com.ntaganira.heritier.ibook.repository.TaxRateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
public class TaxRateService {

    private static final String MODULE = "taxes";

    /** Fallback when no rate is configured at all. */
    public static final BigDecimal FALLBACK_RATE = new BigDecimal("18.00");

    private final TaxRateRepository taxRateRepository;
    private final AuditService auditService;

    public TaxRateService(TaxRateRepository taxRateRepository, AuditService auditService) {
        this.taxRateRepository = taxRateRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<TaxRate> listAll() {
        return taxRateRepository.findAllByOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public List<TaxRate> listActive() {
        return taxRateRepository.findByActiveTrueOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public TaxRate get(Long id) {
        return id == null ? null : taxRateRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public TaxRate defaultRate() {
        return taxRateRepository.findFirstByDefaultRateTrue()
                .orElseGet(() -> taxRateRepository.findByActiveTrueOrderByCodeAsc().stream()
                        .findFirst().orElse(null));
    }

    /** Percentage applied to new invoice and bill lines when nothing is chosen. */
    @Transactional(readOnly = true)
    public BigDecimal defaultRateValue() {
        TaxRate rate = defaultRate();
        return rate == null ? FALLBACK_RATE : rate.getRate();
    }

    @Transactional(readOnly = true)
    public boolean codeExists(String code, Long id) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return id == null
                ? taxRateRepository.existsByCodeIgnoreCase(code.trim())
                : taxRateRepository.existsByCodeIgnoreCaseAndIdNot(code.trim(), id);
    }

    @Transactional
    public TaxRate save(TaxRateForm form, Long id) {
        TaxRate rate = id == null ? new TaxRate() : taxRateRepository.findById(id).orElseThrow();
        rate.setCode(form.code().trim().toUpperCase(Locale.ROOT));
        rate.setName(form.name().trim());
        rate.setRate(form.rate() == null ? BigDecimal.ZERO : form.rate());
        rate.setTreatment(parseTreatment(form.treatment()));
        rate.setDescription(trimToNull(form.description()));
        rate.setActive(form.active());

        // Only a standard-rated, active entry may be the default for new lines.
        boolean wantsDefault = form.defaultRate() && form.active();
        rate.setDefaultRate(wantsDefault);
        TaxRate saved = taxRateRepository.save(rate);
        if (wantsDefault) {
            clearOtherDefaults(saved.getId());
        }
        auditService.log(MODULE, id == null ? "CREATE_TAX_RATE" : "UPDATE_TAX_RATE",
                "taxRate#" + saved.getId(), saved.getCode() + " " + saved.getRate().toPlainString() + "%");
        return saved;
    }

    @Transactional
    public void makeDefault(Long id) {
        TaxRate rate = taxRateRepository.findById(id).orElse(null);
        if (rate == null || !rate.isActive()) {
            return;
        }
        rate.setDefaultRate(true);
        taxRateRepository.save(rate);
        clearOtherDefaults(id);
        auditService.log(MODULE, "DEFAULT_TAX_RATE", "taxRate#" + id, rate.getCode() + " set as default");
    }

    private void clearOtherDefaults(Long keepId) {
        for (TaxRate other : taxRateRepository.findAll()) {
            if (!other.getId().equals(keepId) && other.isDefaultRate()) {
                other.setDefaultRate(false);
                taxRateRepository.save(other);
            }
        }
    }

    @Transactional
    public void toggle(Long id) {
        TaxRate rate = taxRateRepository.findById(id).orElse(null);
        if (rate == null) {
            return;
        }
        rate.setActive(!rate.isActive());
        if (!rate.isActive()) {
            rate.setDefaultRate(false);
        }
        taxRateRepository.save(rate);
        auditService.log(MODULE, "TOGGLE_TAX_RATE", "taxRate#" + id,
                rate.getCode() + (rate.isActive() ? " activated" : " deactivated"));
    }

    private static TaxTreatment parseTreatment(String value) {
        if (value == null || value.isBlank()) {
            return TaxTreatment.STANDARD;
        }
        try {
            return TaxTreatment.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return TaxTreatment.STANDARD;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
