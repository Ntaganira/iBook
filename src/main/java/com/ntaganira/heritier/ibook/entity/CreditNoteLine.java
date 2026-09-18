/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : CreditNoteLine.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : JPA entity for a credit note line item
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.TaxTreatment;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "credit_note_lines")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "creditNote")
@ToString(exclude = "creditNote")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditNoteLine implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credit_note_id", nullable = false)
    private CreditNote creditNote;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_treatment", nullable = false)
    @Builder.Default
    private TaxTreatment taxTreatment = TaxTreatment.STANDARD;

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
