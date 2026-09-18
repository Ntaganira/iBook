/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : Estimate.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : JPA entity for customer estimates (quotes)
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.EstimateStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "estimates")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "lines")
@ToString(exclude = "lines")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Estimate implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "estimate_no", nullable = false, unique = true)
    private String estimateNo;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "estimate_date", nullable = false)
    private LocalDate estimateDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "reference")
    private String reference;

    @Column(name = "customer_message", length = 1000)
    private String customerMessage;

    @Column(name = "notes", length = 1000)
    private String notes;

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
    private EstimateStatus status = EstimateStatus.DRAFT;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "decided_at")
    private LocalDate decidedAt;

    /** Set once the estimate has been turned into an invoice. */
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

    @OneToMany(mappedBy = "estimate", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @OrderBy("sortOrder asc")
    private List<EstimateLine> lines = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addLine(EstimateLine line) {
        line.setEstimate(this);
        lines.add(line);
    }

    public boolean isEditable() {
        return status == EstimateStatus.DRAFT || status == EstimateStatus.SENT;
    }

    public boolean isConverted() {
        return convertedInvoiceId != null;
    }

    public boolean isConvertible() {
        return convertedInvoiceId == null
                && status != EstimateStatus.DECLINED
                && !lines.isEmpty();
    }

    /** EXPIRED is derived from the expiry date rather than stored. */
    public EstimateStatus getDisplayStatus() {
        if (status == EstimateStatus.SENT && expiryDate != null && expiryDate.isBefore(LocalDate.now())) {
            return EstimateStatus.EXPIRED;
        }
        return status;
    }
}
