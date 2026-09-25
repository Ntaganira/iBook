/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : WithholdingSettings.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : The withholding rates and the account they are held in
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The withholding tax table, and the liability account the money sits in until it is handed over.
 *
 * <p>Same gate as payroll, for the same reason. The rates shipped here are a <strong>starting
 * point, not an authority</strong>, and no certificate can be issued until somebody has read them
 * against the current RRA schedule and confirmed them. Withholding is money taken off a supplier's
 * payment and owed to the state; getting the rate wrong shorts either the supplier or the RRA, and
 * both find out eventually.
 *
 * <p>Editing any rate withdraws the confirmation, because approval attaches to figures rather than
 * to a screen.
 */
@Entity
@Table(name = "withholding_settings")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "rates")
@ToString(exclude = "rates")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WithholdingSettings implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "confirmed", nullable = false)
    @Builder.Default
    private boolean confirmed = false;

    @Column(name = "confirmed_by")
    private String confirmedBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "rate_source", length = 500)
    private String rateSource;

    /**
     * Where withheld money is held. A liability, never an expense: it is the supplier's money that
     * the company is holding on the RRA's behalf, and it was never a cost of anything.
     */
    @Column(name = "payable_account_id")
    private Long payableAccountId;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "settings", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @OrderBy("sortOrder asc")
    private List<WithholdingRate> rates = new ArrayList<>();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addRate(WithholdingRate rate) {
        rate.setSettings(this);
        rates.add(rate);
    }

    @Transient
    public boolean isReadyToWithhold() {
        return confirmed && payableAccountId != null && !rates.isEmpty();
    }
}
