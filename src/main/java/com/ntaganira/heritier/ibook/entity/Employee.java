/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : Employee.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : JPA entity for people on the payroll
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.EmployeeStatus;
import com.ntaganira.heritier.ibook.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Somebody on the payroll.
 *
 * <p>Scheme membership is held per person rather than assumed for everybody, because it is not
 * uniform: a casual worker may be outside the pension scheme, medical cover is often only for some
 * grades, and a run that contributed on behalf of somebody who is not a member would pay money to
 * a scheme that will not credit it.
 */
@Entity
@Table(name = "employees")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "components")
@ToString(exclude = "components")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Employee implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_no", nullable = false, unique = true)
    private String employeeNo;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "national_id")
    private String nationalId;

    /** The RSSB number contributions are credited against; a remittance schedule is useless without it. */
    @Column(name = "rssb_number")
    private String rssbNumber;

    @Column(name = "tin_number")
    private String tinNumber;

    @Column(name = "email")
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "job_title")
    private String jobTitle;

    @Column(name = "department")
    private String department;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "basic_salary", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal basicSalary = BigDecimal.ZERO;

    @Column(name = "currency_code", length = 3)
    @Builder.Default
    private String currencyCode = "RWF";

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    @Builder.Default
    private PaymentMethod paymentMethod = PaymentMethod.BANK_TRANSFER;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bank_account")
    private String bankAccount;

    @Column(name = "mobile_money")
    private String mobileMoney;

    @Column(name = "pension_member", nullable = false)
    @Builder.Default
    private boolean pensionMember = true;

    @Column(name = "maternity_member", nullable = false)
    @Builder.Default
    private boolean maternityMember = true;

    @Column(name = "medical_member", nullable = false)
    @Builder.Default
    private boolean medicalMember = false;

    @Column(name = "cbhi_member", nullable = false)
    @Builder.Default
    private boolean cbhiMember = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private EmployeeStatus status = EmployeeStatus.ACTIVE;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @OrderBy("sortOrder asc")
    private List<EmployeeComponent> components = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addComponent(EmployeeComponent component) {
        component.setEmployee(this);
        components.add(component);
    }

    @Transient
    public String getFullName() {
        return (firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName);
    }

    /**
     * Whether this person should appear on a run for the given period. Somebody who left before
     * the period started, or joins after it ends, is not paid for it — and a terminated record is
     * kept rather than deleted, because payslips already issued must still name somebody.
     */
    public boolean isPayableIn(LocalDate periodStart, LocalDate periodEnd) {
        if (status == EmployeeStatus.TERMINATED || status == EmployeeStatus.SUSPENDED) {
            return false;
        }
        if (hireDate != null && periodEnd != null && hireDate.isAfter(periodEnd)) {
            return false;
        }
        return !(endDate != null && periodStart != null && endDate.isBefore(periodStart));
    }

    @Transient
    public boolean isActive() {
        return status == EmployeeStatus.ACTIVE;
    }
}
