/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : StockTransferLine.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : JPA entity for one product moved by a stock transfer
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "stock_transfer_lines")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "transfer")
@ToString(exclude = "transfer")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransferLine implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_transfer_id", nullable = false)
    private StockTransfer transfer;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_sku")
    private String productSku;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "unit")
    private String unit;

    @Column(name = "quantity_sent", precision = 14, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal quantitySent = BigDecimal.ZERO;

    /** What actually arrived; anything short of the quantity sent is lost in transit. */
    @Column(name = "quantity_received", precision = 14, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal quantityReceived = BigDecimal.ZERO;

    @Column(name = "unit_cost", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Transient
    public BigDecimal getShortfall() {
        BigDecimal sent = quantitySent == null ? BigDecimal.ZERO : quantitySent;
        BigDecimal received = quantityReceived == null ? BigDecimal.ZERO : quantityReceived;
        BigDecimal diff = sent.subtract(received);
        return diff.signum() > 0 ? diff : BigDecimal.ZERO;
    }

    @Transient
    public boolean isShort() {
        return getShortfall().signum() > 0;
    }

    @Transient
    public BigDecimal getShortfallValue() {
        return getShortfall().multiply(unitCost == null ? BigDecimal.ZERO : unitCost);
    }

    @Transient
    public BigDecimal getLineValue() {
        BigDecimal sent = quantitySent == null ? BigDecimal.ZERO : quantitySent;
        return sent.multiply(unitCost == null ? BigDecimal.ZERO : unitCost);
    }
}
