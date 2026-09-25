/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : ReportBuilderService.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Saved reports, run against the same figures the standard reports use
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.ReportDefinitionForm;
import com.ntaganira.heritier.ibook.entity.Account;
import com.ntaganira.heritier.ibook.entity.ReportDefinition;
import com.ntaganira.heritier.ibook.entity.ReportDefinitionLine;
import com.ntaganira.heritier.ibook.enums.AccountType;
import com.ntaganira.heritier.ibook.enums.ReportBasis;
import com.ntaganira.heritier.ibook.enums.ReportComparison;
import com.ntaganira.heritier.ibook.enums.ReportRowType;
import com.ntaganira.heritier.ibook.repository.AccountRepository;
import com.ntaganira.heritier.ibook.repository.ReportDefinitionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Runs a saved report definition against the ledger.
 *
 * <p>Every figure comes from {@link ReportService#signedMovementsBetween} or
 * {@link ReportService#signedBalancesUpTo} — the same two methods the profit and loss, the balance
 * sheet and budget-versus-actual all read. That is the whole point of the design: a report builder
 * with its own idea of what a movement is would sooner or later print a number that disagrees with
 * the profit and loss, and there would be no way to tell which was right.
 *
 * <p>A definition stores no figures, so a report written in March and opened in October shows
 * October's books.
 *
 * <p>Two honesty checks run with every report and are shown on the page rather than buried:
 * accounts that have activity but match <em>no</em> row are listed, because a custom report is a
 * selection and a selection can silently drop money; and accounts matched by more than one row are
 * listed too, because they are being counted twice.
 */
@Service
public class ReportBuilderService {

    private static final String MODULE = "reports";
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final ReportDefinitionRepository reportDefinitionRepository;
    private final AccountRepository accountRepository;
    private final ReportService reportService;
    private final AuditService auditService;

    public ReportBuilderService(ReportDefinitionRepository reportDefinitionRepository,
                                AccountRepository accountRepository,
                                ReportService reportService,
                                AuditService auditService) {
        this.reportDefinitionRepository = reportDefinitionRepository;
        this.accountRepository = accountRepository;
        this.reportService = reportService;
        this.auditService = auditService;
    }

    // ----- Queries -----

    @Transactional(readOnly = true)
    public Page<ReportDefinition> list(String q, String basis, Pageable pageable) {
        return reportDefinitionRepository.search(trimToNull(q), parseBasis(basis), pageable);
    }

    @Transactional(readOnly = true)
    public ReportDefinition get(Long id) {
        return id == null ? null : reportDefinitionRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Account> accounts() {
        return accountRepository.findAllByOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public BuilderSummary summary() {
        return new BuilderSummary(
                reportDefinitionRepository.count(),
                reportDefinitionRepository.countByBasis(ReportBasis.MOVEMENT),
                reportDefinitionRepository.countByBasis(ReportBasis.BALANCE));
    }

    // ----- Editing -----

    @Transactional
    public ReportDefinition save(ReportDefinitionForm form, Long id, String username) {
        String name = trimToNull(form.getName());
        if (name == null) {
            throw new IllegalArgumentException("A report needs a name");
        }
        boolean clash = id == null
                ? reportDefinitionRepository.existsByNameIgnoreCase(name)
                : reportDefinitionRepository.existsByNameIgnoreCaseAndIdNot(name, id);
        if (clash) {
            throw new IllegalArgumentException("Another report is already called " + name);
        }

        List<ReportDefinitionForm.Row> rows = form.filledRows();
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("A report with no rows would print nothing");
        }
        boolean anySelection = rows.stream().anyMatch(r -> "ACCOUNTS".equals(r.getRowType()));
        if (!anySelection) {
            throw new IllegalArgumentException("A report needs at least one row that selects "
                    + "accounts — headings and subtotals on their own have nothing to add up");
        }

        ReportDefinition definition;
        if (id == null) {
            definition = new ReportDefinition();
            definition.setCreatedBy(username);
        } else {
            definition = reportDefinitionRepository.findById(id).orElseThrow();
            definition.getLines().clear();
        }

        ReportBasis basis = parseBasis(form.getBasis());
        definition.setName(name);
        definition.setDescription(trimToNull(form.getDescription()));
        definition.setBasis(basis == null ? ReportBasis.MOVEMENT : basis);
        definition.setComparison(parseComparison(form.getComparison()));
        definition.setShowEmptyRows(form.showEmptyRowsValue());

        int order = 0;
        for (ReportDefinitionForm.Row row : rows) {
            ReportRowType rowType = parseRowType(row.getRowType());
            definition.addLine(ReportDefinitionLine.builder()
                    .rowType(rowType)
                    .label(trimToNull(row.getLabel()))
                    .accountType(rowType == ReportRowType.ACCOUNTS
                            ? parseAccountType(row.getAccountType()) : null)
                    .codeFrom(rowType == ReportRowType.ACCOUNTS ? trimToNull(row.getCodeFrom()) : null)
                    .codeTo(rowType == ReportRowType.ACCOUNTS ? trimToNull(row.getCodeTo()) : null)
                    .expanded(row.expandedValue())
                    .invertSign(row.invertSignValue())
                    .sortOrder(order++)
                    .build());
        }

        ReportDefinition saved = reportDefinitionRepository.save(definition);
        auditService.log(MODULE, id == null ? "CREATE_REPORT" : "UPDATE_REPORT",
                "reportDefinition#" + saved.getId(), saved.getName());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        ReportDefinition definition = reportDefinitionRepository.findById(id).orElse(null);
        if (definition == null) {
            return;
        }
        reportDefinitionRepository.delete(definition);
        auditService.log(MODULE, "DELETE_REPORT", "reportDefinition#" + id, definition.getName());
    }

    // ----- Running -----

    /**
     * Works the report out for a period.
     *
     * <p>A {@code BALANCE} report ignores {@code from} entirely — a balance is a position on a
     * date, and pretending it had a start date would invite somebody to read it as a movement.
     */
    @Transactional
    public RunResult run(Long id, LocalDate from, LocalDate to) {
        ReportDefinition definition = reportDefinitionRepository.findById(id).orElseThrow();
        RunResult result = compute(definition, from, to);
        definition.setLastRunAt(LocalDateTime.now());
        reportDefinitionRepository.save(definition);
        return result;
    }

    private RunResult compute(ReportDefinition definition, LocalDate from, LocalDate to) {
        List<Account> accounts = accountRepository.findAllByOrderByCodeAsc();
        Map<Long, BigDecimal> current = figuresFor(definition, from, to);

        LocalDate priorFrom = null;
        LocalDate priorTo = null;
        Map<Long, BigDecimal> prior = null;
        if (definition.isComparing()) {
            if (definition.getComparison() == ReportComparison.PRIOR_YEAR) {
                priorFrom = from.minusYears(1);
                priorTo = to.minusYears(1);
            } else {
                // The period immediately before this one, of the same length, ending the day
                // before it starts — so a March report compares against February, not against
                // "the same dates last month", which for a 31-day month would silently drop a day.
                long days = java.time.temporal.ChronoUnit.DAYS.between(from, to);
                priorTo = from.minusDays(1);
                priorFrom = priorTo.minusDays(days);
            }
            prior = figuresFor(definition, priorFrom, priorTo);
        }

        List<Row> rows = new ArrayList<>();
        Set<Long> matchedAnywhere = new LinkedHashSet<>();
        Set<Long> matchedTwice = new LinkedHashSet<>();
        BigDecimal runningCurrent = BigDecimal.ZERO;
        BigDecimal runningPrior = BigDecimal.ZERO;

        for (ReportDefinitionLine line : definition.getLines()) {
            switch (line.getRowType()) {
                case HEADING -> rows.add(Row.heading(line.getLabel()));
                case SPACER -> rows.add(Row.spacer());
                case SUBTOTAL -> {
                    rows.add(Row.subtotal(line.getLabel(), runningCurrent, runningPrior,
                            definition.isComparing()));
                    runningCurrent = BigDecimal.ZERO;
                    runningPrior = BigDecimal.ZERO;
                }
                case ACCOUNTS -> {
                    BigDecimal rowCurrent = BigDecimal.ZERO;
                    BigDecimal rowPrior = BigDecimal.ZERO;
                    List<Row> detail = new ArrayList<>();
                    for (Account account : accounts) {
                        if (!line.matches(account.getType(), account.getCode())) {
                            continue;
                        }
                        BigDecimal amount = current.getOrDefault(account.getId(), BigDecimal.ZERO);
                        BigDecimal before = prior == null ? BigDecimal.ZERO
                                : prior.getOrDefault(account.getId(), BigDecimal.ZERO);
                        if (line.isInvertSign()) {
                            amount = amount.negate();
                            before = before.negate();
                        }
                        if (!matchedAnywhere.add(account.getId())) {
                            matchedTwice.add(account.getId());
                        }
                        rowCurrent = rowCurrent.add(amount);
                        rowPrior = rowPrior.add(before);
                        if (line.isExpanded()
                                && (definition.isShowEmptyRows() || amount.signum() != 0
                                    || before.signum() != 0)) {
                            detail.add(Row.account(account.getCode(), account.getName(),
                                    amount, before, definition.isComparing()));
                        }
                    }
                    runningCurrent = runningCurrent.add(rowCurrent);
                    runningPrior = runningPrior.add(rowPrior);

                    if (line.isExpanded()) {
                        if (line.getLabel() != null) {
                            rows.add(Row.heading(line.getLabel()));
                        }
                        rows.addAll(detail);
                    } else if (definition.isShowEmptyRows() || rowCurrent.signum() != 0
                            || rowPrior.signum() != 0) {
                        rows.add(Row.account(null,
                                line.getLabel() == null ? describe(line) : line.getLabel(),
                                rowCurrent, rowPrior, definition.isComparing()));
                    }
                }
                default -> { }
            }
        }

        // Only account types the report actually reaches for are candidates for the unassigned
        // warning. A profit and loss never meant to include the bank account, so listing every
        // balance sheet account as "missing" would bury the one revenue account that really was.
        Set<AccountType> typesInScope = new LinkedHashSet<>();
        boolean anyTypeSelected = false;
        for (ReportDefinitionLine line : definition.getLines()) {
            if (line.getRowType() != ReportRowType.ACCOUNTS) {
                continue;
            }
            if (line.getAccountType() == null) {
                anyTypeSelected = true;
            } else {
                typesInScope.add(line.getAccountType());
            }
        }

        List<Unassigned> unassigned = new ArrayList<>();
        for (Account account : accounts) {
            if (matchedAnywhere.contains(account.getId())) {
                continue;
            }
            if (!anyTypeSelected && !typesInScope.contains(account.getType())) {
                continue;
            }
            BigDecimal amount = current.getOrDefault(account.getId(), BigDecimal.ZERO);
            if (amount.signum() != 0) {
                unassigned.add(new Unassigned(account.getCode(), account.getName(), amount));
            }
        }

        List<Unassigned> doubled = new ArrayList<>();
        for (Account account : accounts) {
            if (!matchedTwice.contains(account.getId())) {
                continue;
            }
            doubled.add(new Unassigned(account.getCode(), account.getName(),
                    current.getOrDefault(account.getId(), BigDecimal.ZERO)));
        }

        return new RunResult(definition, from, to, priorFrom, priorTo, rows, unassigned, doubled);
    }

    private Map<Long, BigDecimal> figuresFor(ReportDefinition definition,
                                             LocalDate from, LocalDate to) {
        return definition.getBasis() == ReportBasis.BALANCE
                ? reportService.signedBalancesUpTo(to)
                : reportService.signedMovementsBetween(from, to);
    }

    private static String describe(ReportDefinitionLine line) {
        StringBuilder sb = new StringBuilder();
        if (line.getAccountType() != null) {
            sb.append(line.getAccountType().name());
        }
        if (line.getCodeFrom() != null || line.getCodeTo() != null) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(line.getCodeFrom() == null ? "" : line.getCodeFrom())
                    .append('–')
                    .append(line.getCodeTo() == null ? "" : line.getCodeTo());
        }
        return sb.isEmpty() ? "Accounts" : sb.toString();
    }

    public ReportDefinitionForm toForm(ReportDefinition definition) {
        ReportDefinitionForm form = new ReportDefinitionForm();
        form.setName(definition.getName());
        form.setDescription(definition.getDescription());
        form.setBasis(definition.getBasis().name());
        form.setComparison(definition.getComparison().name());
        form.setShowEmptyRows(definition.isShowEmptyRows());
        for (ReportDefinitionLine line : definition.getLines()) {
            ReportDefinitionForm.Row row = new ReportDefinitionForm.Row();
            row.setRowType(line.getRowType().name());
            row.setLabel(line.getLabel());
            row.setAccountType(line.getAccountType() == null ? null : line.getAccountType().name());
            row.setCodeFrom(line.getCodeFrom());
            row.setCodeTo(line.getCodeTo());
            row.setExpanded(line.isExpanded());
            row.setInvertSign(line.isInvertSign());
            form.getRows().add(row);
        }
        for (int i = 0; i < 4; i++) {
            form.getRows().add(new ReportDefinitionForm.Row());
        }
        return form;
    }

    public static ReportBasis parseBasis(String basis) {
        if (basis == null || basis.isBlank()) {
            return null;
        }
        try {
            return ReportBasis.valueOf(basis.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static ReportComparison parseComparison(String comparison) {
        if (comparison == null || comparison.isBlank()) {
            return ReportComparison.NONE;
        }
        try {
            return ReportComparison.valueOf(comparison.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ReportComparison.NONE;
        }
    }

    private static ReportRowType parseRowType(String rowType) {
        if (rowType == null || rowType.isBlank()) {
            return ReportRowType.ACCOUNTS;
        }
        try {
            return ReportRowType.valueOf(rowType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ReportRowType.ACCOUNTS;
        }
    }

    private static AccountType parseAccountType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return AccountType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * One printed line. The kind travels with it so the template does not have to work out from
     * the shape of the data what it is looking at.
     */
    public record Row(String kind, String code, String label, BigDecimal amount,
                      BigDecimal priorAmount, BigDecimal variance, BigDecimal variancePercent) {

        static Row heading(String label) {
            return new Row("HEADING", null, label, null, null, null, null);
        }

        static Row spacer() {
            return new Row("SPACER", null, null, null, null, null, null);
        }

        static Row account(String code, String label, BigDecimal amount,
                           BigDecimal prior, boolean comparing) {
            return build("ACCOUNT", code, label, amount, prior, comparing);
        }

        static Row subtotal(String label, BigDecimal amount, BigDecimal prior, boolean comparing) {
            return build("SUBTOTAL", null, label, amount, prior, comparing);
        }

        private static Row build(String kind, String code, String label, BigDecimal amount,
                                 BigDecimal prior, boolean comparing) {
            if (!comparing) {
                return new Row(kind, code, label, amount, null, null, null);
            }
            BigDecimal variance = amount.subtract(prior);
            BigDecimal percent = prior.signum() == 0 ? null
                    : variance.multiply(HUNDRED).divide(prior.abs(), 1, RoundingMode.HALF_UP);
            return new Row(kind, code, label, amount, prior, variance, percent);
        }

        public boolean isHeading() {
            return "HEADING".equals(kind);
        }

        public boolean isSpacer() {
            return "SPACER".equals(kind);
        }

        public boolean isSubtotal() {
            return "SUBTOTAL".equals(kind);
        }
    }

    public record Unassigned(String code, String name, BigDecimal amount) {}

    public record RunResult(ReportDefinition definition, LocalDate from, LocalDate to,
                            LocalDate priorFrom, LocalDate priorTo, List<Row> rows,
                            List<Unassigned> unassigned, List<Unassigned> doubleCounted) {

        public boolean hasUnassigned() {
            return !unassigned.isEmpty();
        }

        public boolean hasDoubleCounted() {
            return !doubleCounted.isEmpty();
        }
    }

    public record BuilderSummary(long all, long movementBased, long balanceBased) {}
}
