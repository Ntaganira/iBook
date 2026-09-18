/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : SalesOrder.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : JPA entity for customer sales orders
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.SalesOrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sales_orders")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "lines")
@ToString(exclude = "lines")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, unique = true)
    private String orderNo;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    /** When the customer expects delivery; drives the overdue flag on open orders. */
    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(name = "reference")
    private String reference;

    @Column(name = "currency_code", nullable = false)
    @Builder.Default
    private String currencyCode = "RWF";

    @Column(name = "subtotal", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_amount", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private SalesOrderStatus status = SalesOrderStatus.DRAFT;

    @Column(name = "customer_message", length = 1000)
    private String customerMessage;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "cancelled_reason", length = 500)
    private String cancelledReason;

    /** Set once the order has been turned into an invoice. */
    @Column(name = "converted_invoice_id")
    private Long convertedInvoiceId;

    @Column(name = "converted_invoice_no")
    private String convertedInvoiceNo;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "salesOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @OrderBy("sortOrder asc")
    private List<SalesOrderLine> lines = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addLine(SalesOrderLine line) {
        line.setSalesOrder(this);
        lines.add(line);
    }

    @Transient
    public boolean isEditable() {
        return status == SalesOrderStatus.DRAFT || status == SalesOrderStatus.CONFIRMED;
    }

    @Transient
    public boolean isConverted() {
        return convertedInvoiceId != null;
    }

    @Transient
    public boolean isConvertible() {
        return convertedInvoiceId == null
                && status == SalesOrderStatus.CONFIRMED
                && !lines.isEmpty();
    }

    @Transient
    public boolean isOverdue() {
        return status == SalesOrderStatus.CONFIRMED
                && expectedDate != null
                && expectedDate.isBefore(LocalDate.now());
    }

    @Transient
    public long getDaysLate() {
        if (!isOverdue()) {
            return 0;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(expectedDate, LocalDate.now());
    }
}
