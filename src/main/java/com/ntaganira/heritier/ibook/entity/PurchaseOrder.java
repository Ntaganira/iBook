/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : PurchaseOrder.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : JPA entity for vendor purchase orders
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.PurchaseOrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "purchase_orders")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "lines")
@ToString(exclude = "lines")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, unique = true)
    private String orderNo;

    @Column(name = "vendor_id", nullable = false)
    private Long vendorId;

    @Column(name = "vendor_name", nullable = false)
    private String vendorName;

    @Column(name = "vendor_email")
    private String vendorEmail;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    /** When the goods are due in; drives the overdue flag on confirmed orders. */
    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(name = "reference")
    private String reference;

    @Column(name = "delivery_address", length = 500)
    private String deliveryAddress;

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
    private PurchaseOrderStatus status = PurchaseOrderStatus.DRAFT;

    @Column(name = "memo", length = 1000)
    private String memo;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "cancelled_reason", length = 500)
    private String cancelledReason;

    /** Set once the order has been turned into a vendor bill. */
    @Column(name = "converted_bill_id")
    private Long convertedBillId;

    @Column(name = "converted_bill_no")
    private String convertedBillNo;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @OrderBy("sortOrder asc")
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addLine(PurchaseOrderLine line) {
        line.setPurchaseOrder(this);
        lines.add(line);
    }

    @Transient
    public boolean isEditable() {
        return status == PurchaseOrderStatus.DRAFT || status == PurchaseOrderStatus.CONFIRMED;
    }

    @Transient
    public boolean isConverted() {
        return convertedBillId != null;
    }

    @Transient
    public boolean isConvertible() {
        return convertedBillId == null
                && status == PurchaseOrderStatus.CONFIRMED
                && !lines.isEmpty();
    }

    @Transient
    public boolean isOverdue() {
        return status == PurchaseOrderStatus.CONFIRMED
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
