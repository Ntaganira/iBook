/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : StockMovement.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : JPA entity for a single stock movement
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.MovementType;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_movements")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovement implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_sku")
    private String productSku;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "warehouse_name")
    private String warehouseName;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false)
    private MovementType movementType;

    /** Always stored positive; direction comes from the movement type. */
    @Column(name = "quantity", precision = 14, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "unit_cost", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(name = "movement_date", nullable = false)
    private LocalDate movementDate;

    @Column(name = "reference")
    private String reference;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /** Quantity with its direction applied. */
    public BigDecimal getSignedQuantity() {
        BigDecimal qty = quantity == null ? BigDecimal.ZERO : quantity;
        return movementType == null || movementType.isInbound() ? qty : qty.negate();
    }

    public BigDecimal getValue() {
        BigDecimal qty = quantity == null ? BigDecimal.ZERO : quantity;
        BigDecimal cost = unitCost == null ? BigDecimal.ZERO : unitCost;
        return qty.multiply(cost);
    }
}
