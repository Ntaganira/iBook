/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : AssetCategory.java
 * - Date      : 2026. 09. 19.
 * - User      : Hntaganira
 * - Desc      : JPA entity for fixed asset categories and the policy they carry
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.DepreciationMethod;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "asset_categories")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetCategory implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    /** Optional, because a category imported from free text has no code to import. */
    @Column(name = "code", unique = true)
    private String code;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "depreciation_method", nullable = false)
    @Builder.Default
    private DepreciationMethod depreciationMethod = DepreciationMethod.STRAIGHT_LINE;

    @Column(name = "useful_life_years")
    @Builder.Default
    private int usefulLifeYears = 0;

    @Column(name = "declining_rate", precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal decliningRate = BigDecimal.ZERO;

    @Column(name = "asset_account_id")
    private Long assetAccountId;

    @Column(name = "asset_account_code")
    private String assetAccountCode;

    @Column(name = "accumulated_account_id")
    private Long accumulatedAccountId;

    @Column(name = "accumulated_account_code")
    private String accumulatedAccountCode;

    @Column(name = "expense_account_id")
    private Long expenseAccountId;

    @Column(name = "expense_account_code")
    private String expenseAccountCode;

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

    /** A category with nothing to offer would apply blanks over the register's own defaults. */
    @Transient
    public boolean isCarryingPolicy() {
        return usefulLifeYears > 0 || assetAccountId != null
                || accumulatedAccountId != null || expenseAccountId != null;
    }
}
