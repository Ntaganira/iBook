/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : RecurringJournalForm.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Recurring journal form backing bean with editable lines
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import org.springframework.util.AutoPopulatingList;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class RecurringJournalForm {

    private String name;
    private String description;
    private String frequency;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer maxOccurrences;
    private Boolean autoPost;
    private String reference;
    private String memo;
    private String notes;
    private boolean activateNow;
    private List<Line> lines = new AutoPopulatingList<>(Line.class);

    public static RecurringJournalForm empty() {
        RecurringJournalForm form = new RecurringJournalForm();
        form.setFrequency("MONTHLY");
        form.setStartDate(LocalDate.now());
        for (int i = 0; i < 4; i++) {
            form.getLines().get(i);
        }
        return form;
    }

    public BigDecimal totalDebits() {
        BigDecimal total = BigDecimal.ZERO;
        if (lines != null) {
            for (Line line : lines) {
                if (line != null) {
                    total = total.add(line.debitValue());
                }
            }
        }
        return total;
    }

    public BigDecimal totalCredits() {
        BigDecimal total = BigDecimal.ZERO;
        if (lines != null) {
            for (Line line : lines) {
                if (line != null) {
                    total = total.add(line.creditValue());
                }
            }
        }
        return total;
    }

    public boolean balanced() {
        return totalDebits().compareTo(totalCredits()) == 0 && totalDebits().signum() > 0;
    }

    public boolean autoPostValue() {
        return Boolean.TRUE.equals(autoPost);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFrequency() {
        return frequency;
    }

    public void setFrequency(String frequency) {
        this.frequency = frequency;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Integer getMaxOccurrences() {
        return maxOccurrences;
    }

    public void setMaxOccurrences(Integer maxOccurrences) {
        this.maxOccurrences = maxOccurrences;
    }

    public Boolean getAutoPost() {
        return autoPost;
    }

    public void setAutoPost(Boolean autoPost) {
        this.autoPost = autoPost;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public boolean isActivateNow() {
        return activateNow;
    }

    public void setActivateNow(boolean activateNow) {
        this.activateNow = activateNow;
    }

    public List<Line> getLines() {
        return lines;
    }

    public void setLines(List<Line> lines) {
        this.lines = lines;
    }

    public static class Line {

        private Long accountId;
        private String memo;
        private BigDecimal debit;
        private BigDecimal credit;

        public boolean isFilled() {
            return accountId != null && (debitValue().signum() > 0 || creditValue().signum() > 0);
        }

        public BigDecimal debitValue() {
            return debit == null ? BigDecimal.ZERO : debit;
        }

        public BigDecimal creditValue() {
            return credit == null ? BigDecimal.ZERO : credit;
        }

        public Long getAccountId() {
            return accountId;
        }

        public void setAccountId(Long accountId) {
            this.accountId = accountId;
        }

        public String getMemo() {
            return memo;
        }

        public void setMemo(String memo) {
            this.memo = memo;
        }

        public BigDecimal getDebit() {
            return debit;
        }

        public void setDebit(BigDecimal debit) {
            this.debit = debit;
        }

        public BigDecimal getCredit() {
            return credit;
        }

        public void setCredit(BigDecimal credit) {
            this.credit = credit;
        }
    }
}
