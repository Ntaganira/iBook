/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : ProductBundle.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : JPA entity for a product assembled from other products
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "product_bundles")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "components")
@ToString(exclude = "components")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductBundle implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Doubles as the reference on the stock movements an assembly writes. */
    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    /** The product that is sold; it carries stock of its own once assembled. */
    @Column(name = "bundle_product_id", nullable = false, unique = true)
    private Long bundleProductId;

    @Column(name = "bundle_product_sku")
    private String bundleProductSku;

    @Column(name = "bundle_product_name")
    private String bundleProductName;

    /** Sum of the component costs; what one assembled bundle is capitalised at. */
    @Column(name = "unit_cost", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal unitCost = BigDecimal.ZERO;

    /** The bundle product's selling price, copied for comparison against the rolled-up cost. */
    @Column(name = "selling_price", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal sellingPrice = BigDecimal.ZERO;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "bundle", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @OrderBy("sortOrder asc")
    private List<ProductBundleComponent> components = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addComponent(ProductBundleComponent component) {
        component.setBundle(this);
        components.add(component);
    }

    @Transient
    public int getComponentCount() {
        return components.size();
    }

    /** What one bundle makes over the cost of its parts; negative means it sells at a loss. */
    @Transient
    public BigDecimal getMargin() {
        BigDecimal price = sellingPrice == null ? BigDecimal.ZERO : sellingPrice;
        return price.subtract(unitCost == null ? BigDecimal.ZERO : unitCost);
    }

    @Transient
    public boolean isLossMaking() {
        return getMargin().signum() < 0;
    }
}
