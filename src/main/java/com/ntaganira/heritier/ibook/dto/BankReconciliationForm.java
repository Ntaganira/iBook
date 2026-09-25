/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : BankReconciliationForm.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Bank reconciliation form backing bean with editable statement lines
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import org.springframework.util.AutoPopulatingList;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class BankReconciliationForm {

    private Long accountId;
    private LocalDate statementDate;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private String notes;
    private List<Line> lines = new AutoPopulatingList<>(Line.class);

    public static BankReconciliationForm empty() {
        BankReconciliationForm form = new BankReconciliationForm();
        LocalDate today = LocalDate.now();
        form.setStatementDate(today.withDayOfMonth(today.lengthOfMonth()));
        form.setOpeningBalance(BigDecimal.ZERO);
        form.setClosingBalance(BigDecimal.ZERO);
        for (int i = 0; i < 10; i++) {
            form.getLines().get(i);
        }
        return form;
    }

    public List<Line> filledLines() {
        return lines == null ? List.of() : lines.stream().filter(l -> l != null && l.isFilled()).toList();
    }

    public BigDecimal openingValue() {
        return openingBalance == null ? BigDecimal.ZERO : openingBalance;
    }

    public BigDecimal closingValue() {
        return closingBalance == null ? BigDecimal.ZERO : closingBalance;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public LocalDate getStatementDate() {
        return statementDate;
    }

    public void setStatementDate(LocalDate statementDate) {
        this.statementDate = statementDate;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public void setOpeningBalance(BigDecimal openingBalance) {
        this.openingBalance = openingBalance;
    }

    public BigDecimal getClosingBalance() {
        return closingBalance;
    }

    public void setClosingBalance(BigDecimal closingBalance) {
        this.closingBalance = closingBalance;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<Line> getLines() {
        return lines;
    }

    public void setLines(List<Line> lines) {
        this.lines = lines;
    }

    public static class Line {

        private LocalDate lineDate;
        private String description;
        private String reference;
        private BigDecimal moneyIn;
        private BigDecimal moneyOut;

        /** A line needs a date and some money; a date on its own is somebody part-way through typing. */
        public boolean isFilled() {
            return lineDate != null && (inValue().signum() != 0 || outValue().signum() != 0);
        }

        public BigDecimal inValue() {
            return moneyIn == null ? BigDecimal.ZERO : moneyIn;
        }

        public BigDecimal outValue() {
            return moneyOut == null ? BigDecimal.ZERO : moneyOut;
        }

        public LocalDate getLineDate() {
            return lineDate;
        }

        public void setLineDate(LocalDate lineDate) {
            this.lineDate = lineDate;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getReference() {
            return reference;
        }

        public void setReference(String reference) {
            this.reference = reference;
        }

        public BigDecimal getMoneyIn() {
            return moneyIn;
        }

        public void setMoneyIn(BigDecimal moneyIn) {
            this.moneyIn = moneyIn;
        }

        public BigDecimal getMoneyOut() {
            return moneyOut;
        }

        public void setMoneyOut(BigDecimal moneyOut) {
            this.moneyOut = moneyOut;
        }
    }
}
