/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : JournalEntryForm.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : Journal entry form backing bean with editable lines
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class JournalEntryForm {

    private LocalDate entryDate;
    private String reference;
    private String memo;
    private boolean postNow;
    private List<Line> lines = new ArrayList<>();

    public static JournalEntryForm empty() {
        JournalEntryForm form = new JournalEntryForm();
        form.setEntryDate(LocalDate.now());
        form.setLines(new ArrayList<>(List.of(new Line(), new Line(), new Line())));
        return form;
    }

    public BigDecimal totalDebits() {
        BigDecimal total = BigDecimal.ZERO;
        if (lines != null) {
            for (Line line : lines) {
                total = total.add(line.getDebitValue());
            }
        }
        return total;
    }

    public BigDecimal totalCredits() {
        BigDecimal total = BigDecimal.ZERO;
        if (lines != null) {
            for (Line line : lines) {
                total = total.add(line.getCreditValue());
            }
        }
        return total;
    }

    public boolean balanced() {
        return totalDebits().compareTo(totalCredits()) == 0;
    }

    public boolean hasContent() {
        if (lines == null) {
            return false;
        }
        for (Line line : lines) {
            if (line.getAccountId() != null || line.getDebitValue().signum() > 0 || line.getCreditValue().signum() > 0) {
                return true;
            }
        }
        return false;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(LocalDate entryDate) {
        this.entryDate = entryDate;
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

    public boolean isPostNow() {
        return postNow;
    }

    public void setPostNow(boolean postNow) {
        this.postNow = postNow;
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

        public BigDecimal getDebitValue() {
            return debit == null ? BigDecimal.ZERO : debit;
        }

        public BigDecimal getCreditValue() {
            return credit == null ? BigDecimal.ZERO : credit;
        }
    }
}