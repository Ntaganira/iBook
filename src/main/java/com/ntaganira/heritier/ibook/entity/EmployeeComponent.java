/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : EmployeeComponent.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : A pay component attached to one person, with an optional override
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.PayrollComponentKind;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * One allowance or deduction as it applies to one person.
 *
 * <p>An override is held here rather than by copying the component, so a rate that applies to
 * everybody can still be changed in one place while one person's negotiated figure stays theirs.
 * Everything else — whether it is taxable, which account it hits — is read from the component at
 * calculation time, because those are properties of the allowance rather than of the person.
 */
@Entity
@Table(name = "employee_components")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "employee")
@ToString(exclude = "employee")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeComponent implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(name = "component_id", nullable = false)
    private Long componentId;

    @Column(name = "component_code")
    private String componentCode;

    @Column(name = "component_name")
    private String componentName;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    @Builder.Default
    private PayrollComponentKind kind = PayrollComponentKind.ALLOWANCE;

    /** Left empty the component's own figure is used. */
    @Column(name = "override_amount", precision = 16, scale = 2)
    private BigDecimal overrideAmount;

    @Column(name = "sort_order")
    @Builder.Default
    private int sortOrder = 0;

    @Transient
    public boolean isOverridden() {
        return overrideAmount != null;
    }
}
