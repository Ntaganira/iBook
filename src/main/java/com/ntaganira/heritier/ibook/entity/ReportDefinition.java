/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : ReportDefinition.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : A saved, re-runnable report over the ledger
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.ReportBasis;
import com.ntaganira.heritier.ibook.enums.ReportComparison;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A saved report: which accounts appear, under which headings, in which order.
 *
 * <p>A definition holds <strong>no figures</strong>. It says where to look, and the numbers are
 * fetched from the ledger every time it runs — so a report saved in March and opened in October
 * shows October's books rather than a stale snapshot, and nothing here can drift away from what
 * the profit and loss says.
 *
 * <p>The two bases are not interchangeable. {@code MOVEMENT} asks what happened between two dates,
 * which is the question a profit and loss answers and the only sensible one for revenue and
 * expense. {@code BALANCE} asks what an account stood at on a date, which is the balance sheet's
 * question and is meaningless for a trading account, whose balance is only ever "since when".
 */
@Entity
@Table(name = "report_definitions")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "lines")
@ToString(exclude = "lines")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportDefinition implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "basis", nullable = false)
    @Builder.Default
    private ReportBasis basis = ReportBasis.MOVEMENT;

    @Enumerated(EnumType.STRING)
    @Column(name = "comparison", nullable = false)
    @Builder.Default
    private ReportComparison comparison = ReportComparison.NONE;

    /**
     * Whether rows the ledger has nothing for are still printed. Off by default, because a report
     * of forty accounts where thirty-five are zero hides the five that matter — but on when the
     * shape of the report is the point, such as a statutory return whose lines must all appear.
     */
    @Column(name = "show_empty_rows", nullable = false)
    @Builder.Default
    private boolean showEmptyRows = false;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    @OneToMany(mappedBy = "definition", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @OrderBy("sortOrder asc")
    private List<ReportDefinitionLine> lines = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addLine(ReportDefinitionLine line) {
        line.setDefinition(this);
        lines.add(line);
    }

    @Transient
    public boolean isMovementBased() {
        return basis == ReportBasis.MOVEMENT;
    }

    @Transient
    public boolean isComparing() {
        return comparison != ReportComparison.NONE;
    }
}
