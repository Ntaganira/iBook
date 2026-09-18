/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : StockCountForm.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Stock count sheet form backing bean
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import org.springframework.util.AutoPopulatingList;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class StockCountForm {

    private Long warehouseId;
    private LocalDate countDate;
    private String countedBy;
    private String reference;
    private String notes;
    private boolean completeNow;
    private List<Line> lines = new AutoPopulatingList<>(Line.class);

    public static StockCountForm empty() {
        StockCountForm form = new StockCountForm();
        form.setCountDate(LocalDate.now());
        return form;
    }

    /** Only lines somebody actually wrote a number on take part in the count. */
    public List<Line> countedLines() {
        List<Line> counted = new ArrayList<>();
        if (lines != null) {
            for (Line line : lines) {
                if (line.isCounted()) {
                    counted.add(line);
                }
            }
        }
        return counted;
    }

    public boolean hasCount() {
        return !countedLines().isEmpty();
    }

    public int countedCount() {
        return countedLines().size();
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public LocalDate getCountDate() {
        return countDate;
    }

    public void setCountDate(LocalDate countDate) {
        this.countDate = countDate;
    }

    public String getCountedBy() {
        return countedBy;
    }

    public void setCountedBy(String countedBy) {
        this.countedBy = countedBy;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public boolean isCompleteNow() {
        return completeNow;
    }

    public void setCompleteNow(boolean completeNow) {
        this.completeNow = completeNow;
    }

    public List<Line> getLines() {
        return lines;
    }

    public void setLines(List<Line> lines) {
        this.lines = new AutoPopulatingList<>(Line.class);
        if (lines != null) {
            this.lines.addAll(lines);
        }
    }

    public static class Line {

        private Long productId;
        private String productSku;
        private String productName;
        private String unit;
        private BigDecimal expectedQuantity;
        private BigDecimal countedQuantity;
        private BigDecimal unitCost;

        /** A blank box means the line was not counted, which is not the same as counting zero. */
        public boolean isCounted() {
            return productId != null && countedQuantity != null;
        }

        public BigDecimal expectedValue() {
            return expectedQuantity == null ? BigDecimal.ZERO : expectedQuantity;
        }

        public BigDecimal unitCostValue() {
            return unitCost == null ? BigDecimal.ZERO : unitCost;
        }

        public BigDecimal variance() {
            if (countedQuantity == null) {
                return BigDecimal.ZERO;
            }
            return countedQuantity.subtract(expectedValue());
        }

        public BigDecimal varianceValue() {
            return variance().multiply(unitCostValue());
        }

        public Long getProductId() {
            return productId;
        }

        public void setProductId(Long productId) {
            this.productId = productId;
        }

        public String getProductSku() {
            return productSku;
        }

        public void setProductSku(String productSku) {
            this.productSku = productSku;
        }

        public String getProductName() {
            return productName;
        }

        public void setProductName(String productName) {
            this.productName = productName;
        }

        public String getUnit() {
            return unit;
        }

        public void setUnit(String unit) {
            this.unit = unit;
        }

        public BigDecimal getExpectedQuantity() {
            return expectedQuantity;
        }

        public void setExpectedQuantity(BigDecimal expectedQuantity) {
            this.expectedQuantity = expectedQuantity;
        }

        public BigDecimal getCountedQuantity() {
            return countedQuantity;
        }

        public void setCountedQuantity(BigDecimal countedQuantity) {
            this.countedQuantity = countedQuantity;
        }

        public BigDecimal getUnitCost() {
            return unitCost;
        }

        public void setUnitCost(BigDecimal unitCost) {
            this.unitCost = unitCost;
        }
    }
}
