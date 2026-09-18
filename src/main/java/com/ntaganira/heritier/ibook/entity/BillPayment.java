/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : BillPayment.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : JPA entity for payments made against a vendor bill
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bill_payments")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "bill")
@ToString(exclude = "bill")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillPayment implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bill_id", nullable = false)
    private Bill bill;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "amount", precision = 16, scale = 2, nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false)
    @Builder.Default
    private PaymentMethod method = PaymentMethod.BANK_TRANSFER;

    @Column(name = "reference")
    private String reference;

    @Column(name = "paid_from_account_id")
    private Long paidFromAccountId;

    @Column(name = "paid_from_account_code")
    private String paidFromAccountCode;

    @Column(name = "paid_from_account_name")
    private String paidFromAccountName;

    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
