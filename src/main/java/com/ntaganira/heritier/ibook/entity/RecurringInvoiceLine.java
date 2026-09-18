/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : RecurringInvoiceLine.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : JPA entity for a recurring invoice schedule line item
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "recurring_invoice_lines")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "recurringInvoice")
@ToString(exclude = "recurringInvoice")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringInvoiceLine implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recurring_invoice_id", nullable = false)
    private RecurringInvoice recurringInvoice;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_sku")
    private String productSku;

    @Column(name = "quantity", precision = 16, scale = 4)
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(name = "unit_price", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "tax_rate", precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "tax_rate_id")
    private Long taxRateId;

    @Column(name = "line_subtotal", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal lineSubtotal = BigDecimal.ZERO;

    @Column(name = "line_tax", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal lineTax = BigDecimal.ZERO;

    @Column(name = "line_total", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal lineTotal = BigDecimal.ZERO;

    @Column(name = "revenue_account_id")
    private Long revenueAccountId;

    @Column(name = "revenue_account_code")
    private String revenueAccountCode;

    @Column(name = "revenue_account_name")
    private String revenueAccountName;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;
}
