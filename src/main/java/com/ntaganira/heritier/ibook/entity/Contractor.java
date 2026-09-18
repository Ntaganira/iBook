/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : Contractor.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : JPA entity for engaged contractors
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.ContractorRateType;
import com.ntaganira.heritier.ibook.enums.ContractorType;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "contractors")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contractor implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "contractor_type", nullable = false)
    @Builder.Default
    private ContractorType contractorType = ContractorType.INDIVIDUAL;

    @Column(name = "trade")
    private String trade;

    @Column(name = "email")
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "mobile_money")
    private String mobileMoney;

    @Column(name = "tax_id")
    private String taxId;

    @Column(name = "street")
    private String street;

    @Column(name = "city")
    private String city;

    @Column(name = "country")
    private String country;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bank_account")
    private String bankAccount;

    @Column(name = "contract_start")
    private LocalDate contractStart;

    @Column(name = "contract_end")
    private LocalDate contractEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_type", nullable = false)
    @Builder.Default
    private ContractorRateType rateType = ContractorRateType.FIXED;

    @Column(name = "rate_amount", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal rateAmount = BigDecimal.ZERO;

    /**
     * Reference only. Withholding is not applied to any posting — that waits on
     * withholding fields on bill and expense lines.
     */
    @Column(name = "withholding_rate", precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal withholdingRate = BigDecimal.ZERO;

    /** Optional link to the supplier record the money actually runs through. */
    @Column(name = "vendor_id")
    private Long vendorId;

    @Column(name = "vendor_name")
    private String vendorName;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Transient
    public boolean isLinked() {
        return vendorId != null;
    }

    /** An engagement whose end date has passed, so no new work should be booked to it. */
    @Transient
    public boolean isExpired() {
        return contractEnd != null && contractEnd.isBefore(LocalDate.now());
    }

    @Transient
    public boolean isExpiringSoon() {
        if (contractEnd == null || isExpired()) {
            return false;
        }
        return !contractEnd.isAfter(LocalDate.now().plusDays(30));
    }

    @Transient
    public long getDaysToExpiry() {
        if (contractEnd == null) {
            return 0;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), contractEnd);
    }
}
