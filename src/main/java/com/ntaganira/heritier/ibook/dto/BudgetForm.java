/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : BudgetForm.java
 * - Date      : 2026. 09. 20.
 * - User      : Hntaganira
 * - Desc      : Budget form backing bean with one editable line per budgeted account
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import org.springframework.util.AutoPopulatingList;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class BudgetForm {

    private String name;
    private Integer fiscalYear;
    private LocalDate startDate;
    private String description;
    private String notes;
    private boolean approveNow;
    private List<Line> lines = new AutoPopulatingList<>(Line.class);

    public static BudgetForm empty() {
        BudgetForm form = new BudgetForm();
        LocalDate firstOfYear = LocalDate.now().withDayOfYear(1);
        form.setFiscalYear(firstOfYear.getYear());
        form.setStartDate(firstOfYear);
        for (int i = 0; i < 5; i++) {
            form.getLines().get(i);
        }
        return form;
    }

    /** What the grid adds up to, so the form can show a total without a round trip. */
    public BigDecimal totalFor(String type) {
        BigDecimal total = BigDecimal.ZERO;
        if (lines != null) {
            for (Line line : lines) {
                if (line != null && type.equals(line.getAccountType())) {
                    total = total.add(line.effectiveAnnual());
                }
            }
        }
        return total;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getFiscalYear() {
        return fiscalYear;
    }

    public void setFiscalYear(Integer fiscalYear) {
        this.fiscalYear = fiscalYear;
    }

    public int fiscalYearValue() {
        return fiscalYear == null ? LocalDate.now().getYear() : fiscalYear;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public boolean isApproveNow() {
        return approveNow;
    }

    public void setApproveNow(boolean approveNow) {
        this.approveNow = approveNow;
    }

    public List<Line> getLines() {
        return lines;
    }

    public void setLines(List<Line> lines) {
        this.lines = lines;
    }

    public static class Line {

        private Long accountId;
        /** Carried on the line so the grid can total revenue and expense without the chart. */
        private String accountType;
        private BigDecimal annualAmount;
        private BigDecimal m1;
        private BigDecimal m2;
        private BigDecimal m3;
        private BigDecimal m4;
        private BigDecimal m5;
        private BigDecimal m6;
        private BigDecimal m7;
        private BigDecimal m8;
        private BigDecimal m9;
        private BigDecimal m10;
        private BigDecimal m11;
        private BigDecimal m12;
        private String notes;

        public boolean isEmpty() {
            return accountId == null;
        }

        /** True when the twelve boxes were filled in, so they win over the annual figure. */
        public boolean hasMonthlyDetail() {
            return monthlyTotal().signum() != 0;
        }

        public BigDecimal monthlyTotal() {
            BigDecimal total = BigDecimal.ZERO;
            for (int m = 1; m <= 12; m++) {
                total = total.add(zero(monthAt(m)));
            }
            return total;
        }

        public BigDecimal effectiveAnnual() {
            return hasMonthlyDetail() ? monthlyTotal() : zero(annualAmount);
        }

        public BigDecimal monthAt(int month) {
            return switch (month) {
                case 1 -> m1;
                case 2 -> m2;
                case 3 -> m3;
                case 4 -> m4;
                case 5 -> m5;
                case 6 -> m6;
                case 7 -> m7;
                case 8 -> m8;
                case 9 -> m9;
                case 10 -> m10;
                case 11 -> m11;
                case 12 -> m12;
                default -> BigDecimal.ZERO;
            };
        }

        private static BigDecimal zero(BigDecimal value) {
            return value == null ? BigDecimal.ZERO : value;
        }

        public Long getAccountId() {
            return accountId;
        }

        public void setAccountId(Long accountId) {
            this.accountId = accountId;
        }

        public String getAccountType() {
            return accountType;
        }

        public void setAccountType(String accountType) {
            this.accountType = accountType;
        }

        public BigDecimal getAnnualAmount() {
            return annualAmount;
        }

        public void setAnnualAmount(BigDecimal annualAmount) {
            this.annualAmount = annualAmount;
        }

        public BigDecimal getM1() {
            return m1;
        }

        public void setM1(BigDecimal m1) {
            this.m1 = m1;
        }

        public BigDecimal getM2() {
            return m2;
        }

        public void setM2(BigDecimal m2) {
            this.m2 = m2;
        }

        public BigDecimal getM3() {
            return m3;
        }

        public void setM3(BigDecimal m3) {
            this.m3 = m3;
        }

        public BigDecimal getM4() {
            return m4;
        }

        public void setM4(BigDecimal m4) {
            this.m4 = m4;
        }

        public BigDecimal getM5() {
            return m5;
        }

        public void setM5(BigDecimal m5) {
            this.m5 = m5;
        }

        public BigDecimal getM6() {
            return m6;
        }

        public void setM6(BigDecimal m6) {
            this.m6 = m6;
        }

        public BigDecimal getM7() {
            return m7;
        }

        public void setM7(BigDecimal m7) {
            this.m7 = m7;
        }

        public BigDecimal getM8() {
            return m8;
        }

        public void setM8(BigDecimal m8) {
            this.m8 = m8;
        }

        public BigDecimal getM9() {
            return m9;
        }

        public void setM9(BigDecimal m9) {
            this.m9 = m9;
        }

        public BigDecimal getM10() {
            return m10;
        }

        public void setM10(BigDecimal m10) {
            this.m10 = m10;
        }

        public BigDecimal getM11() {
            return m11;
        }

        public void setM11(BigDecimal m11) {
            this.m11 = m11;
        }

        public BigDecimal getM12() {
            return m12;
        }

        public void setM12(BigDecimal m12) {
            this.m12 = m12;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }
    }
}
