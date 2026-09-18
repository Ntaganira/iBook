/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : Expense.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : JPA entity for direct expenses paid without a vendor bill
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.ExpenseStatus;
import com.ntaganira.heritier.ibook.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "expenses")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "lines")
@ToString(exclude = "lines")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Expense implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expense_no", nullable = false, unique = true)
    private String expenseNo;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    /** Optional: an expense may be paid to someone who is not a registered supplier. */
    @Column(name = "vendor_id")
    private Long vendorId;

    @Column(name = "payee_name", nullable = false)
    private String payeeName;

    /** The cash or bank account the money left — 10xx or 11xx. */
    @Column(name = "payment_account_id", nullable = false)
    private Long paymentAccountId;

    @Column(name = "payment_account_code")
    private String paymentAccountCode;

    @Column(name = "payment_account_name")
    private String paymentAccountName;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    @Builder.Default
    private PaymentMethod paymentMethod = PaymentMethod.CASH;

    @Column(name = "reference")
    private String reference;

    @Column(name = "currency_code", nullable = false)
    @Builder.Default
    private String currencyCode = "RWF";

    @Column(name = "subtotal", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tax_amount", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ExpenseStatus status = ExpenseStatus.DRAFT;

    @Column(name = "memo", length = 1000)
    private String memo;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @OrderBy("sortOrder asc")
    private List<ExpenseLine> lines = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addLine(ExpenseLine line) {
        line.setExpense(this);
        lines.add(line);
    }

    @Transient
    public boolean isEditable() {
        return status == ExpenseStatus.DRAFT;
    }

    @Transient
    public boolean isPosted() {
        return journalEntryId != null;
    }
}
