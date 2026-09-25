/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : Forecast.java
 * - Date      : 2026. 09. 20.
 * - User      : Hntaganira
 * - Desc      : JPA entity for a twelve-month revenue or expense forecast
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.ForecastKind;
import com.ntaganira.heritier.ibook.enums.ForecastMethod;
import com.ntaganira.heritier.ibook.enums.ForecastStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "forecasts")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "lines")
@ToString(exclude = "lines")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Forecast implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private ForecastKind kind;

    /** The month the first column covers; the twelve run from here. */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false)
    @Builder.Default
    private ForecastMethod method = ForecastMethod.AVERAGE;

    /** How many months of posted history the opening figures were worked out from. */
    @Column(name = "basis_months")
    @Builder.Default
    private int basisMonths = 6;

    @Column(name = "growth_percent", precision = 9, scale = 2)
    @Builder.Default
    private BigDecimal growthPercent = BigDecimal.ZERO;

    /** Only set when the figures were copied from a budget. */
    @Column(name = "source_budget_id")
    private Long sourceBudgetId;

    @Column(name = "source_budget_name")
    private String sourceBudgetName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ForecastStatus status = ForecastStatus.DRAFT;

    @Column(name = "total_amount", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "published_by")
    private String publishedBy;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "forecast", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc, id asc")
    @Builder.Default
    private List<ForecastLine> lines = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addLine(ForecastLine line) {
        line.setForecast(this);
        lines.add(line);
    }

    @Transient
    public boolean isRevenue() {
        return kind == ForecastKind.REVENUE;
    }

    @Transient
    public boolean isDraft() {
        return status == ForecastStatus.DRAFT;
    }

    @Transient
    public boolean isPublished() {
        return status == ForecastStatus.PUBLISHED;
    }

    @Transient
    public boolean isArchived() {
        return status == ForecastStatus.ARCHIVED;
    }

    /**
     * An archived forecast is history — the cash flow it fed may already have been acted on, so it
     * is kept readable rather than editable.
     */
    @Transient
    public boolean isEditable() {
        return status != ForecastStatus.ARCHIVED;
    }

    @Transient
    public LocalDate getEndDate() {
        return startDate == null ? null : startDate.plusMonths(12).minusDays(1);
    }


    /**
     * How many months of posted history the figures were measured over. The seasonal method reads a
     * full year a year back, and the copy and manual methods have no basis of their own, so the
     * twelve months before the start are what they are shown against.
     */
    @Transient
    public int getHistoryMonths() {
        if (method != null && method.usesHistory() && method != ForecastMethod.SEASONAL) {
            return Math.max(1, basisMonths);
        }
        return 12;
    }
    /**
     * True when at least one figure was typed over what the method produced. A forecast typed in
     * from the start has nothing to have been typed over, so it is not an adjustment of anything
     * and saying otherwise would put a warning on every hand-made forecast.
     */
    @Transient
    public boolean isAdjusted() {
        if (method == ForecastMethod.MANUAL) {
            return false;
        }
        for (ForecastLine line : lines) {
            if (line.isAdjusted()) {
                return true;
            }
        }
        return false;
    }

    @Transient
    public BigDecimal getMonthlyAverage() {
        BigDecimal total = totalAmount == null ? BigDecimal.ZERO : totalAmount;
        return total.divide(new BigDecimal(12), 2, java.math.RoundingMode.HALF_UP);
    }
}
