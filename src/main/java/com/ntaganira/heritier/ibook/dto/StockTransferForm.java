/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : StockTransferForm.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Stock transfer form backing bean with editable line items
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import org.springframework.util.AutoPopulatingList;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class StockTransferForm {

    private Long fromWarehouseId;
    private Long toWarehouseId;
    private LocalDate transferDate;
    private String reference;
    private String notes;
    private boolean completeNow;
    private List<Line> lines = new AutoPopulatingList<>(Line.class);

    public static StockTransferForm empty() {
        StockTransferForm form = new StockTransferForm();
        form.setTransferDate(LocalDate.now());
        for (int i = 0; i < 3; i++) {
            form.getLines().get(i);
        }
        return form;
    }

    public BigDecimal totalSent() {
        BigDecimal total = BigDecimal.ZERO;
        if (lines != null) {
            for (Line line : lines) {
                total = total.add(line.sentValue());
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal totalReceived() {
        BigDecimal total = BigDecimal.ZERO;
        if (lines != null) {
            for (Line line : lines) {
                total = total.add(line.receivedValue());
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    public boolean hasContent() {
        if (lines == null) {
            return false;
        }
        for (Line line : lines) {
            if (line.isFilled()) {
                return true;
            }
        }
        return false;
    }

    public List<Line> filledLines() {
        List<Line> filled = new ArrayList<>();
        if (lines != null) {
            for (Line line : lines) {
                if (line.isFilled()) {
                    filled.add(line);
                }
            }
        }
        return filled;
    }

    public Long getFromWarehouseId() {
        return fromWarehouseId;
    }

    public void setFromWarehouseId(Long fromWarehouseId) {
        this.fromWarehouseId = fromWarehouseId;
    }

    public Long getToWarehouseId() {
        return toWarehouseId;
    }

    public void setToWarehouseId(Long toWarehouseId) {
        this.toWarehouseId = toWarehouseId;
    }

    public LocalDate getTransferDate() {
        return transferDate;
    }

    public void setTransferDate(LocalDate transferDate) {
        this.transferDate = transferDate;
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
        private BigDecimal quantitySent;
        private BigDecimal quantityReceived;
        private BigDecimal unitCost;

        public boolean isFilled() {
            return productId != null && sentValue().signum() > 0;
        }

        public BigDecimal sentValue() {
            return quantitySent == null ? BigDecimal.ZERO : quantitySent;
        }

        /** An empty received box means everything arrived, which is the ordinary case. */
        public BigDecimal receivedValue() {
            return quantityReceived == null ? sentValue() : quantityReceived;
        }

        public BigDecimal unitCostValue() {
            return unitCost == null ? BigDecimal.ZERO : unitCost;
        }

        public BigDecimal shortfall() {
            BigDecimal diff = sentValue().subtract(receivedValue());
            return diff.signum() > 0 ? diff : BigDecimal.ZERO;
        }

        public Long getProductId() {
            return productId;
        }

        public void setProductId(Long productId) {
            this.productId = productId;
        }

        public BigDecimal getQuantitySent() {
            return quantitySent;
        }

        public void setQuantitySent(BigDecimal quantitySent) {
            this.quantitySent = quantitySent;
        }

        public BigDecimal getQuantityReceived() {
            return quantityReceived;
        }

        public void setQuantityReceived(BigDecimal quantityReceived) {
            this.quantityReceived = quantityReceived;
        }

        public BigDecimal getUnitCost() {
            return unitCost;
        }

        public void setUnitCost(BigDecimal unitCost) {
            this.unitCost = unitCost;
        }
    }
}
