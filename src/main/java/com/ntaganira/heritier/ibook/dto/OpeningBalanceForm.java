/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : OpeningBalanceForm.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Bulk edit form for account opening balances
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public class OpeningBalanceForm {

    /** Keyed by account id; bound from inputs named balances[<id>]. */
    private Map<Long, BigDecimal> balances = new LinkedHashMap<>();

    public Map<Long, BigDecimal> getBalances() {
        return balances;
    }

    public void setBalances(Map<Long, BigDecimal> balances) {
        this.balances = balances;
    }
}
