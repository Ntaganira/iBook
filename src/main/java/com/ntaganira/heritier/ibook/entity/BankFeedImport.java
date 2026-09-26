/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : BankFeedImport.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : One statement file brought in against a bank account
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.FeedImportStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One statement file brought in against a bank account.
 *
 * <p><strong>This is a file import, not a bank connection.</strong> Nothing here dials out to
 * anybody: a live feed needs credentials and an agreement with the bank, and neither exists. What
 * does exist is the file every bank will give you, and reading that is the part that makes the rest
 * of the work possible. The page says which it is rather than implying a connection.
 *
 * <p>Importing <strong>posts nothing</strong>. It brings lines in as uncategorised and they stay
 * that way until somebody says what each one is. A file that wrote entries to the ledger on
 * arrival would be posting on the strength of somebody else's document with nobody in the loop.
 *
 * <p>The file's own checksum is kept so the same statement cannot be imported twice without
 * somebody being told — importing a month twice would double every figure on it.
 */
@Entity
@Table(name = "bank_feed_imports")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "lines")
@ToString(exclude = "lines")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankFeedImport implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false)
    private String reference;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "account_code")
    private String accountCode;

    @Column(name = "account_name")
    private String accountName;

    @Column(name = "file_name")
    private String fileName;

    /**
     * A digest of the file's contents. Held so importing the same statement twice can be refused
     * rather than silently doubling a month.
     */
    @Column(name = "checksum", length = 64)
    private String checksum;

    @Column(name = "imported_at", nullable = false)
    private LocalDateTime importedAt;

    @Column(name = "imported_by")
    private String importedBy;

    @Column(name = "line_count")
    @Builder.Default
    private int lineCount = 0;

    @Column(name = "skipped_count")
    @Builder.Default
    private int skippedCount = 0;

    @Column(name = "first_line_date")
    private LocalDate firstLineDate;

    @Column(name = "last_line_date")
    private LocalDate lastLineDate;

    @Column(name = "total_in", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal totalIn = BigDecimal.ZERO;

    @Column(name = "total_out", precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal totalOut = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private FeedImportStatus status = FeedImportStatus.IMPORTED;

    @Column(name = "notes", length = 1000)
    private String notes;

    @OneToMany(mappedBy = "feedImport", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @OrderBy("lineDate asc, id asc")
    private List<BankFeedLine> lines = new ArrayList<>();

    public void addLine(BankFeedLine line) {
        line.setFeedImport(this);
        lines.add(line);
    }

    @Transient
    public boolean isDiscarded() {
        return status == FeedImportStatus.DISCARDED;
    }

    @Transient
    public BigDecimal getNet() {
        return zero(totalIn).subtract(zero(totalOut));
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
