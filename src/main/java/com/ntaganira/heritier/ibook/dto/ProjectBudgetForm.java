/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : ProjectBudgetForm.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Project budget form backing bean with one editable line per account
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import org.springframework.util.AutoPopulatingList;

import java.math.BigDecimal;
import java.util.List;

public class ProjectBudgetForm {

    private Long projectId;
    private String description;
    private BigDecimal budgetedHours;
    private String notes;
    private boolean approveNow;
    private List<Line> lines = new AutoPopulatingList<>(Line.class);

    public static ProjectBudgetForm empty() {
        ProjectBudgetForm form = new ProjectBudgetForm();
        for (int i = 0; i < 6; i++) {
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
                    total = total.add(line.amountValue());
                }
            }
        }
        return total;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getBudgetedHours() {
        return budgetedHours;
    }

    public void setBudgetedHours(BigDecimal budgetedHours) {
        this.budgetedHours = budgetedHours;
    }

    public BigDecimal budgetedHoursValue() {
        return budgetedHours == null ? BigDecimal.ZERO : budgetedHours;
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
        private String accountType;
        private BigDecimal amount;
        private String notes;

        public boolean isFilled() {
            return accountId != null;
        }

        public BigDecimal amountValue() {
            return amount == null ? BigDecimal.ZERO : amount;
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

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }
    }
}
