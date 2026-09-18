/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : TaxRate.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : JPA entity for configurable tax rates
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.TaxTreatment;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tax_rates")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxRate implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "rate", precision = 6, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal rate = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "treatment", nullable = false)
    @Builder.Default
    private TaxTreatment treatment = TaxTreatment.STANDARD;

    @Column(name = "description")
    private String description;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean defaultRate = false;

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

    /** Label used in line-item pickers, e.g. "Standard VAT (18%)". */
    public String getLabel() {
        return name + " (" + rate.stripTrailingZeros().toPlainString() + "%)";
    }
}
