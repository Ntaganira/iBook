/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : Product.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : JPA entity for products and services
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.ProductType;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sku", nullable = false, unique = true)
    private String sku;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    @Builder.Default
    private ProductType type = ProductType.GOOD;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "category_name")
    private String categoryName;

    @Column(name = "brand_id")
    private Long brandId;

    /** Denormalised brand name; also holds free text typed before brands became their own records. */
    @Column(name = "brand")
    private String brand;

    @Column(name = "unit")
    @Builder.Default
    private String unit = "each";

    @Column(name = "cost_price", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal costPrice = BigDecimal.ZERO;

    @Column(name = "selling_price", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal sellingPrice = BigDecimal.ZERO;

    @Column(name = "tax_rate_id")
    private Long taxRateId;

    @Column(name = "income_account_id")
    private Long incomeAccountId;

    @Column(name = "expense_account_id")
    private Long expenseAccountId;

    @Column(name = "reorder_level", precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal reorderLevel = BigDecimal.ZERO;

    /** Services and non-stocked goods are excluded from valuation and stock counts. */
    @Column(name = "track_stock", nullable = false)
    @Builder.Default
    private boolean trackStock = true;

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

    public BigDecimal getMargin() {
        BigDecimal sell = sellingPrice == null ? BigDecimal.ZERO : sellingPrice;
        BigDecimal cost = costPrice == null ? BigDecimal.ZERO : costPrice;
        return sell.subtract(cost);
    }
}
