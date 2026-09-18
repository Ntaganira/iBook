/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : ProductBundleForm.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Product bundle form backing bean with editable components
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import org.springframework.util.AutoPopulatingList;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class ProductBundleForm {

    private String code;
    private String name;
    private String description;
    private Long bundleProductId;
    private boolean active;
    private List<Line> lines = new AutoPopulatingList<>(Line.class);

    public static ProductBundleForm empty() {
        ProductBundleForm form = new ProductBundleForm();
        form.setActive(true);
        for (int i = 0; i < 3; i++) {
            form.getLines().get(i).setQuantity(BigDecimal.ONE);
        }
        return form;
    }

    public BigDecimal rolledUpCost() {
        BigDecimal total = BigDecimal.ZERO;
        if (lines != null) {
            for (Line line : lines) {
                total = total.add(line.lineCost());
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    public boolean hasComponents() {
        return !filledLines().isEmpty();
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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
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

    public Long getBundleProductId() {
        return bundleProductId;
    }

    public void setBundleProductId(Long bundleProductId) {
        this.bundleProductId = bundleProductId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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
        private BigDecimal quantity;
        private BigDecimal unitCost;

        public boolean isFilled() {
            return productId != null && quantityValue().signum() > 0;
        }

        public BigDecimal quantityValue() {
            return quantity == null ? BigDecimal.ZERO : quantity;
        }

        public BigDecimal unitCostValue() {
            return unitCost == null ? BigDecimal.ZERO : unitCost;
        }

        public BigDecimal lineCost() {
            return quantityValue().multiply(unitCostValue()).setScale(2, RoundingMode.HALF_UP);
        }

        public Long getProductId() {
            return productId;
        }

        public void setProductId(Long productId) {
            this.productId = productId;
        }

        public BigDecimal getQuantity() {
            return quantity;
        }

        public void setQuantity(BigDecimal quantity) {
            this.quantity = quantity;
        }

        public BigDecimal getUnitCost() {
            return unitCost;
        }

        public void setUnitCost(BigDecimal unitCost) {
            this.unitCost = unitCost;
        }
    }
}
