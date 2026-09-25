/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : WithholdingSettingsForm.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : Withholding rate table form backing bean
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import org.springframework.util.AutoPopulatingList;

import java.math.BigDecimal;
import java.util.List;

public class WithholdingSettingsForm {

    private Long payableAccountId;
    private String rateSource;
    private List<Row> rates = new AutoPopulatingList<>(Row.class);

    public List<Row> filledRates() {
        return rates == null ? List.of() : rates.stream().filter(r -> r != null && r.isFilled()).toList();
    }

    public Long getPayableAccountId() {
        return payableAccountId;
    }

    public void setPayableAccountId(Long payableAccountId) {
        this.payableAccountId = payableAccountId;
    }

    public String getRateSource() {
        return rateSource;
    }

    public void setRateSource(String rateSource) {
        this.rateSource = rateSource;
    }

    public List<Row> getRates() {
        return rates;
    }

    public void setRates(List<Row> rates) {
        this.rates = rates;
    }

    public static class Row {

        private String code;
        private String name;
        private BigDecimal rate;
        private String appliesTo;

        /** A rate needs a code and a name; a zero rate is meaningful, an unnamed one is not. */
        public boolean isFilled() {
            return notBlank(code) && notBlank(name);
        }

        private static boolean notBlank(String value) {
            return value != null && !value.isBlank();
        }

        public BigDecimal rateValue() {
            return rate == null ? BigDecimal.ZERO : rate;
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

        public BigDecimal getRate() {
            return rate;
        }

        public void setRate(BigDecimal rate) {
            this.rate = rate;
        }

        public String getAppliesTo() {
            return appliesTo;
        }

        public void setAppliesTo(String appliesTo) {
            this.appliesTo = appliesTo;
        }
    }
}
