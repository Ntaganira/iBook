/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : ProductBundleComponent.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : JPA entity for one product that goes into a bundle
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "product_bundle_components")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "bundle")
@ToString(exclude = "bundle")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductBundleComponent implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_bundle_id", nullable = false)
    private ProductBundle bundle;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_sku")
    private String productSku;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "unit")
    private String unit;

    /** How many of this component go into one bundle. */
    @Column(name = "quantity", precision = 14, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ONE;

    /** The component's cost price when the bundle was last saved. */
    @Column(name = "unit_cost", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Transient
    public BigDecimal getLineCost() {
        BigDecimal qty = quantity == null ? BigDecimal.ZERO : quantity;
        return qty.multiply(unitCost == null ? BigDecimal.ZERO : unitCost);
    }
}
