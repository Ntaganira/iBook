/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : PayrollSettingsForm.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Payroll rates and accounts form backing bean with editable PAYE bands
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import org.springframework.util.AutoPopulatingList;

import java.math.BigDecimal;
import java.util.List;

public class PayrollSettingsForm {

    private BigDecimal pensionEmployeeRate;
    private BigDecimal pensionEmployerRate;
    private BigDecimal occupationalHazardRate;
    private BigDecimal maternityEmployeeRate;
    private BigDecimal maternityEmployerRate;
    private BigDecimal medicalEmployeeRate;
    private BigDecimal medicalEmployerRate;
    private BigDecimal cbhiEmployeeRate;
    private BigDecimal contributionCeiling;
    private Boolean pensionDeductibleForPaye;
    private String rateSource;

    private Long wagesExpenseAccountId;
    private Long employerContributionAccountId;
    private Long payePayableAccountId;
    private Long rssbPayableAccountId;
    private Long cbhiPayableAccountId;
    private Long netPayPayableAccountId;

    private List<Band> bands = new AutoPopulatingList<>(Band.class);

    public boolean pensionDeductibleValue() {
        return Boolean.TRUE.equals(pensionDeductibleForPaye);
    }

    /** Bands somebody actually filled in, in the order they were typed. */
    public List<Band> filledBands() {
        return bands == null ? List.of() : bands.stream().filter(b -> b != null && b.isFilled()).toList();
    }

    public BigDecimal getPensionEmployeeRate() {
        return pensionEmployeeRate;
    }

    public void setPensionEmployeeRate(BigDecimal pensionEmployeeRate) {
        this.pensionEmployeeRate = pensionEmployeeRate;
    }

    public BigDecimal getPensionEmployerRate() {
        return pensionEmployerRate;
    }

    public void setPensionEmployerRate(BigDecimal pensionEmployerRate) {
        this.pensionEmployerRate = pensionEmployerRate;
    }

    public BigDecimal getOccupationalHazardRate() {
        return occupationalHazardRate;
    }

    public void setOccupationalHazardRate(BigDecimal occupationalHazardRate) {
        this.occupationalHazardRate = occupationalHazardRate;
    }

    public BigDecimal getMaternityEmployeeRate() {
        return maternityEmployeeRate;
    }

    public void setMaternityEmployeeRate(BigDecimal maternityEmployeeRate) {
        this.maternityEmployeeRate = maternityEmployeeRate;
    }

    public BigDecimal getMaternityEmployerRate() {
        return maternityEmployerRate;
    }

    public void setMaternityEmployerRate(BigDecimal maternityEmployerRate) {
        this.maternityEmployerRate = maternityEmployerRate;
    }

    public BigDecimal getMedicalEmployeeRate() {
        return medicalEmployeeRate;
    }

    public void setMedicalEmployeeRate(BigDecimal medicalEmployeeRate) {
        this.medicalEmployeeRate = medicalEmployeeRate;
    }

    public BigDecimal getMedicalEmployerRate() {
        return medicalEmployerRate;
    }

    public void setMedicalEmployerRate(BigDecimal medicalEmployerRate) {
        this.medicalEmployerRate = medicalEmployerRate;
    }

    public BigDecimal getCbhiEmployeeRate() {
        return cbhiEmployeeRate;
    }

    public void setCbhiEmployeeRate(BigDecimal cbhiEmployeeRate) {
        this.cbhiEmployeeRate = cbhiEmployeeRate;
    }

    public BigDecimal getContributionCeiling() {
        return contributionCeiling;
    }

    public void setContributionCeiling(BigDecimal contributionCeiling) {
        this.contributionCeiling = contributionCeiling;
    }

    public Boolean getPensionDeductibleForPaye() {
        return pensionDeductibleForPaye;
    }

    public void setPensionDeductibleForPaye(Boolean pensionDeductibleForPaye) {
        this.pensionDeductibleForPaye = pensionDeductibleForPaye;
    }

    public String getRateSource() {
        return rateSource;
    }

    public void setRateSource(String rateSource) {
        this.rateSource = rateSource;
    }

    public Long getWagesExpenseAccountId() {
        return wagesExpenseAccountId;
    }

    public void setWagesExpenseAccountId(Long wagesExpenseAccountId) {
        this.wagesExpenseAccountId = wagesExpenseAccountId;
    }

    public Long getEmployerContributionAccountId() {
        return employerContributionAccountId;
    }

    public void setEmployerContributionAccountId(Long employerContributionAccountId) {
        this.employerContributionAccountId = employerContributionAccountId;
    }

    public Long getPayePayableAccountId() {
        return payePayableAccountId;
    }

    public void setPayePayableAccountId(Long payePayableAccountId) {
        this.payePayableAccountId = payePayableAccountId;
    }

    public Long getRssbPayableAccountId() {
        return rssbPayableAccountId;
    }

    public void setRssbPayableAccountId(Long rssbPayableAccountId) {
        this.rssbPayableAccountId = rssbPayableAccountId;
    }

    public Long getCbhiPayableAccountId() {
        return cbhiPayableAccountId;
    }

    public void setCbhiPayableAccountId(Long cbhiPayableAccountId) {
        this.cbhiPayableAccountId = cbhiPayableAccountId;
    }

    public Long getNetPayPayableAccountId() {
        return netPayPayableAccountId;
    }

    public void setNetPayPayableAccountId(Long netPayPayableAccountId) {
        this.netPayPayableAccountId = netPayPayableAccountId;
    }

    public List<Band> getBands() {
        return bands;
    }

    public void setBands(List<Band> bands) {
        this.bands = bands;
    }

    public static class Band {

        private BigDecimal bandFloor;
        private BigDecimal bandCeiling;
        private BigDecimal rate;

        /**
         * A band with a floor counts even when its rate is zero — the nil-rate band is the whole
         * point of the bottom of a PAYE table, and dropping it because it charges nothing would
         * tax the first franc somebody earns.
         */
        public boolean isFilled() {
            return bandFloor != null;
        }

        public BigDecimal floorValue() {
            return bandFloor == null ? BigDecimal.ZERO : bandFloor;
        }

        public BigDecimal rateValue() {
            return rate == null ? BigDecimal.ZERO : rate;
        }

        public BigDecimal getBandFloor() {
            return bandFloor;
        }

        public void setBandFloor(BigDecimal bandFloor) {
            this.bandFloor = bandFloor;
        }

        public BigDecimal getBandCeiling() {
            return bandCeiling;
        }

        public void setBandCeiling(BigDecimal bandCeiling) {
            this.bandCeiling = bandCeiling;
        }

        public BigDecimal getRate() {
            return rate;
        }

        public void setRate(BigDecimal rate) {
            this.rate = rate;
        }
    }
}
