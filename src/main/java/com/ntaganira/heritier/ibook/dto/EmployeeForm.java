/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : EmployeeForm.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Employee form backing bean
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record EmployeeForm(
        @NotBlank(message = "{emp.firstNameRequired}") @Size(max = 80) String firstName,
        @NotBlank(message = "{emp.lastNameRequired}") @Size(max = 80) String lastName,
        @Size(max = 40) String nationalId,
        @Size(max = 40) String rssbNumber,
        @Size(max = 40) String tinNumber,
        @Email(message = "{emp.emailInvalid}") @Size(max = 160) String email,
        @Size(max = 40) String phone,
        @Size(max = 120) String jobTitle,
        @Size(max = 120) String department,
        LocalDate hireDate,
        LocalDate endDate,
        @DecimalMin(value = "0.00", message = "{emp.salaryNegative}") BigDecimal basicSalary,
        String currencyCode,
        String paymentMethod,
        @Size(max = 120) String bankName,
        @Size(max = 60) String bankAccount,
        @Size(max = 40) String mobileMoney,
        String status,
        @Size(max = 1000) String notes,
        /* Boxed throughout: an unchecked box submits nothing, and a record cannot bind null to a
         * primitive boolean — the fault that already broke the vendor and warehouse forms. */
        Boolean pensionMember,
        Boolean maternityMember,
        Boolean medicalMember,
        Boolean cbhiMember,
        /*
         * Which allowances and deductions this person carries. A checkbox only submits when it is
         * ticked, so ticked ids alone cannot be lined up with a column of amounts — the fourth row
         * ticked would take the first row's figure. The form therefore submits a hidden id for
         * every row it drew, and the overrides are aligned against that instead.
         */
        List<Long> componentIds,
        List<Long> allComponentIds,
        List<BigDecimal> componentOverrides) {

    public static EmployeeForm empty() {
        return new EmployeeForm(null, null, null, null, null, null, null, null, null,
                LocalDate.now(), null, BigDecimal.ZERO, "RWF", "BANK_TRANSFER", null, null, null,
                "ACTIVE", null, Boolean.TRUE, Boolean.TRUE, Boolean.FALSE, Boolean.TRUE,
                List.of(), List.of(), List.of());
    }

    public boolean pensionMemberValue() {
        return Boolean.TRUE.equals(pensionMember);
    }

    public boolean maternityMemberValue() {
        return Boolean.TRUE.equals(maternityMember);
    }

    public boolean medicalMemberValue() {
        return Boolean.TRUE.equals(medicalMember);
    }

    public boolean cbhiMemberValue() {
        return Boolean.TRUE.equals(cbhiMember);
    }

    public BigDecimal basicSalaryValue() {
        return basicSalary == null ? BigDecimal.ZERO : basicSalary;
    }

    public List<Long> componentIdsValue() {
        return componentIds == null ? List.of() : componentIds;
    }

    public boolean carries(Long componentId) {
        return componentId != null && componentIdsValue().contains(componentId);
    }

    /**
     * The override typed against a component, found through the hidden row ids rather than by
     * position in the ticked list.
     */
    public BigDecimal overrideFor(Long componentId) {
        if (componentId == null || allComponentIds == null || componentOverrides == null) {
            return null;
        }
        int index = allComponentIds.indexOf(componentId);
        if (index < 0 || index >= componentOverrides.size()) {
            return null;
        }
        return componentOverrides.get(index);
    }
}
