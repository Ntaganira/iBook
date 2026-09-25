/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : ReportDefinitionForm.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Report builder form backing bean with editable rows
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import org.springframework.util.AutoPopulatingList;

import java.util.List;

public class ReportDefinitionForm {

    private String name;
    private String description;
    private String basis;
    private String comparison;
    private Boolean showEmptyRows;
    private List<Row> rows = new AutoPopulatingList<>(Row.class);

    public static ReportDefinitionForm empty() {
        ReportDefinitionForm form = new ReportDefinitionForm();
        form.setBasis("MOVEMENT");
        form.setComparison("NONE");
        for (int i = 0; i < 8; i++) {
            form.getRows().get(i);
        }
        return form;
    }

    public boolean showEmptyRowsValue() {
        return Boolean.TRUE.equals(showEmptyRows);
    }

    /** Rows somebody actually filled in, in the order they were typed. */
    public List<Row> filledRows() {
        return rows == null ? List.of() : rows.stream().filter(r -> r != null && r.isFilled()).toList();
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

    public String getBasis() {
        return basis;
    }

    public void setBasis(String basis) {
        this.basis = basis;
    }

    public String getComparison() {
        return comparison;
    }

    public void setComparison(String comparison) {
        this.comparison = comparison;
    }

    public Boolean getShowEmptyRows() {
        return showEmptyRows;
    }

    public void setShowEmptyRows(Boolean showEmptyRows) {
        this.showEmptyRows = showEmptyRows;
    }

    public List<Row> getRows() {
        return rows;
    }

    public void setRows(List<Row> rows) {
        this.rows = rows;
    }

    public static class Row {

        private String rowType;
        private String label;
        private String accountType;
        private String codeFrom;
        private String codeTo;
        private Boolean expanded;
        private Boolean invertSign;

        /**
         * A heading or a subtotal counts as filled once it has a label; an accounts row needs
         * something to select by, or it would print a heading with nothing under it.
         */
        public boolean isFilled() {
            if (rowType == null || rowType.isBlank()) {
                return false;
            }
            if ("ACCOUNTS".equals(rowType)) {
                return notBlank(accountType) || notBlank(codeFrom) || notBlank(codeTo);
            }
            if ("SPACER".equals(rowType)) {
                return true;
            }
            return notBlank(label);
        }

        private static boolean notBlank(String value) {
            return value != null && !value.isBlank();
        }

        public boolean expandedValue() {
            return Boolean.TRUE.equals(expanded);
        }

        public boolean invertSignValue() {
            return Boolean.TRUE.equals(invertSign);
        }

        public String getRowType() {
            return rowType;
        }

        public void setRowType(String rowType) {
            this.rowType = rowType;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getAccountType() {
            return accountType;
        }

        public void setAccountType(String accountType) {
            this.accountType = accountType;
        }

        public String getCodeFrom() {
            return codeFrom;
        }

        public void setCodeFrom(String codeFrom) {
            this.codeFrom = codeFrom;
        }

        public String getCodeTo() {
            return codeTo;
        }

        public void setCodeTo(String codeTo) {
            this.codeTo = codeTo;
        }

        public Boolean getExpanded() {
            return expanded;
        }

        public void setExpanded(Boolean expanded) {
            this.expanded = expanded;
        }

        public Boolean getInvertSign() {
            return invertSign;
        }

        public void setInvertSign(Boolean invertSign) {
            this.invertSign = invertSign;
        }
    }
}
