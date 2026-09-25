/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : JobCostingService.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Tags posted ledger lines to a project and reports what each job made
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.entity.Account;
import com.ntaganira.heritier.ibook.entity.JournalLine;
import com.ntaganira.heritier.ibook.entity.Project;
import com.ntaganira.heritier.ibook.enums.AccountType;
import com.ntaganira.heritier.ibook.repository.AccountRepository;
import com.ntaganira.heritier.ibook.repository.JournalLineRepository;
import com.ntaganira.heritier.ibook.repository.ProjectRepository;
import com.ntaganira.heritier.ibook.repository.TimeEntryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Job costing answers "what did this job actually make", and it answers it from the ledger rather
 * than from anything this module invents.
 *
 * <p>Tagging a posted line is deliberately <em>not</em> a posting. The account, the date and the
 * amount are left exactly as the document wrote them, so no trial balance, profit and loss or VAT
 * return can move because somebody tagged a line; all that changes is which job the line is read
 * under. That is why it is allowed on a posted entry at all, where editing one would not be.
 *
 * <p>Only revenue and expense lines can be tagged. Tagging the receivable side of an invoice, or
 * the bank side of a payment, would make "cost of this job" include money moving between two of
 * the company's own pockets, which is not a cost of anything.
 */
@Service
public class JobCostingService {

    private static final String MODULE = "projects";
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final JournalLineRepository journalLineRepository;
    private final AccountRepository accountRepository;
    private final ProjectRepository projectRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final AuditService auditService;

    public JobCostingService(JournalLineRepository journalLineRepository,
                             AccountRepository accountRepository,
                             ProjectRepository projectRepository,
                             TimeEntryRepository timeEntryRepository,
                             AuditService auditService) {
        this.journalLineRepository = journalLineRepository;
        this.accountRepository = accountRepository;
        this.projectRepository = projectRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.auditService = auditService;
    }

    // ----- Queries -----

    /**
     * Flattened for the page rather than handed over as entities: the entry behind a line is lazy,
     * and reading it back a row at a time while the template renders is both a query per row and a
     * bet that the session is still open when it happens.
     */
    @Transactional(readOnly = true)
    public Page<CostLine> lines(LocalDate from, LocalDate to, Long accountId, Long projectId,
                                boolean untaggedOnly, Pageable pageable) {
        List<Long> trading = tradingAccountIds();
        if (trading.isEmpty()) {
            return Page.empty(pageable);
        }
        Page<JournalLine> page = untaggedOnly
                ? journalLineRepository.untaggedLines(from, to, trading, accountId, pageable)
                : journalLineRepository.costingLines(from, to, trading, accountId, projectId, pageable);
        return page.map(JobCostingService::toCostLine);
    }

    /**
     * The page lists only what it will accept. Offering the receivable or bank side of an entry
     * and then refusing it on submit reads as a fault rather than as the rule it is.
     */
    private List<Long> tradingAccountIds() {
        List<Long> ids = new ArrayList<>();
        for (Account account : accountRepository.findAllByOrderByCodeAsc()) {
            if (isTrading(account.getType())) {
                ids.add(account.getId());
            }
        }
        return ids;
    }

    private static CostLine toCostLine(JournalLine line) {
        return new CostLine(line.getId(), line.getEntry().getId(), line.getEntry().getEntryNo(),
                line.getEntry().getEntryDate(), line.getEntry().getReference(),
                line.getAccountCode(), line.getAccountName(),
                line.getMemo() == null ? line.getEntry().getMemo() : line.getMemo(),
                line.getDebitValue(), line.getCreditValue(), line.getProjectCode());
    }

    @Transactional(readOnly = true)
    public Map<Long, Account> accountsById() {
        Map<Long, Account> map = new LinkedHashMap<>();
        for (Account account : accountRepository.findAllByOrderByCodeAsc()) {
            map.put(account.getId(), account);
        }
        return map;
    }

    /** The accounts a job can carry: trading accounts only, in chart order. */
    @Transactional(readOnly = true)
    public List<Account> costingAccounts() {
        List<Account> rows = new ArrayList<>();
        for (Account account : accountRepository.findAllByOrderByCodeAsc()) {
            if (isTrading(account.getType())) {
                rows.add(account);
            }
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<Project> taggableProjects() {
        List<Project> rows = new ArrayList<>();
        for (Project project : projectRepository.findAllByOrderByCodeAsc()) {
            if (!project.isCancelled()) {
                rows.add(project);
            }
        }
        return rows;
    }

    // ----- Tagging -----

    /**
     * Tags the chosen lines to a job. Every line is read again inside the transaction rather than
     * trusted from the page that listed it, and anything that is not a trading line is refused and
     * counted rather than silently dropped.
     */
    @Transactional
    public TagResult tag(List<Long> lineIds, Long projectId, String username) {
        if (lineIds == null || lineIds.isEmpty()) {
            throw new IllegalArgumentException("Tick the ledger lines to be tagged first");
        }
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("That project no longer exists"));
        if (project.isCancelled()) {
            throw new IllegalStateException("A cancelled project cannot take new cost");
        }

        Map<Long, Account> accounts = accountsById();
        int tagged = 0;
        int refused = 0;
        int alreadyOn = 0;
        for (Long id : lineIds) {
            JournalLine line = journalLineRepository.findById(id).orElse(null);
            if (line == null) {
                refused++;
                continue;
            }
            Account account = accounts.get(line.getAccountId());
            if (account == null || !isTrading(account.getType())) {
                refused++;
                continue;
            }
            if (projectId.equals(line.getProjectId())) {
                alreadyOn++;
                continue;
            }
            line.setProjectId(project.getId());
            line.setProjectCode(project.getCode());
            journalLineRepository.save(line);
            tagged++;
        }
        if (tagged == 0 && alreadyOn == 0) {
            throw new IllegalArgumentException(
                    "None of those lines can be tagged. Only revenue and expense lines carry a job.");
        }
        auditService.log(MODULE, "TAG_JOB_COST", "project#" + project.getId(),
                tagged + " ledger lines tagged to " + project.getCode());
        return new TagResult(project, tagged, alreadyOn, refused);
    }

    @Transactional
    public int untag(List<Long> lineIds, String username) {
        if (lineIds == null || lineIds.isEmpty()) {
            throw new IllegalArgumentException("Tick the ledger lines to be untagged first");
        }
        int cleared = 0;
        for (Long id : lineIds) {
            JournalLine line = journalLineRepository.findById(id).orElse(null);
            if (line == null || line.getProjectId() == null) {
                continue;
            }
            line.setProjectId(null);
            line.setProjectCode(null);
            journalLineRepository.save(line);
            cleared++;
        }
        if (cleared == 0) {
            throw new IllegalArgumentException("None of those lines carried a job to take off");
        }
        auditService.log(MODULE, "UNTAG_JOB_COST", "ledger",
                cleared + " ledger lines untagged");
        return cleared;
    }

    // ----- What the ledger says each job made -----

    /**
     * Signed trading movement per account for one job, in the same direction the profit and loss
     * uses, so a job's revenue and the revenue on the report cannot drift apart in meaning.
     */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> signedTotalsForProject(Long projectId, LocalDate from, LocalDate to) {
        Map<Long, Account> accounts = accountsById();
        Map<Long, BigDecimal> signed = new LinkedHashMap<>();
        for (Object[] row : journalLineRepository.taggedTotalsForProject(projectId, from, to)) {
            Long accountId = ((Number) row[0]).longValue();
            Account account = accounts.get(accountId);
            if (account == null) {
                continue;
            }
            signed.put(accountId, sign(account.getType(), amount(row[1]), amount(row[2])));
        }
        return signed;
    }

    /**
     * One row per job over a date range.
     *
     * <p>The ledger figures and the timesheet figures are two different measures of the same work
     * and are reported side by side rather than added together. The wages behind booked hours are
     * already in the ledger through payroll — under whichever expense account payroll used — so
     * adding a timesheet's cost of time to a tagged ledger cost would count the same wages twice.
     * The ledger column is what the books say; the time column is what the job is thought to have
     * consumed.
     */
    @Transactional(readOnly = true)
    public Profitability profitability(LocalDate from, LocalDate to, String statusFilter) {
        Map<Long, Account> accounts = accountsById();
        Map<Long, BigDecimal[]> byProject = new LinkedHashMap<>();
        for (Object[] row : journalLineRepository.taggedTotalsBetween(from, to)) {
            Long projectId = ((Number) row[0]).longValue();
            Account account = accounts.get(((Number) row[1]).longValue());
            if (account == null || !isTrading(account.getType())) {
                continue;
            }
            BigDecimal signed = sign(account.getType(), amount(row[2]), amount(row[3]));
            BigDecimal[] pair = byProject.computeIfAbsent(projectId,
                    key -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            if (account.getType() == AccountType.REVENUE) {
                pair[0] = pair[0].add(signed);
            } else {
                pair[1] = pair[1].add(signed);
            }
        }

        com.ntaganira.heritier.ibook.enums.ProjectStatus wanted =
                ProjectService.parseStatus(statusFilter);
        List<ProfitRow> rows = new ArrayList<>();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalHours = BigDecimal.ZERO;
        BigDecimal totalTimeCost = BigDecimal.ZERO;
        BigDecimal totalInvoiced = BigDecimal.ZERO;
        BigDecimal totalAwaiting = BigDecimal.ZERO;
        int untagged = 0;

        for (Project project : projectRepository.findAllByOrderByCodeAsc()) {
            if (wanted != null && project.getStatus() != wanted) {
                continue;
            }
            BigDecimal[] pair = byProject.get(project.getId());
            BigDecimal revenue = pair == null ? BigDecimal.ZERO : pair[0];
            BigDecimal cost = pair == null ? BigDecimal.ZERO : pair[1];
            if (pair == null) {
                untagged++;
            }
            BigDecimal hours = zero(timeEntryRepository.hoursOn(project.getId()));
            BigDecimal timeCost = zero(timeEntryRepository.costOn(project.getId()));
            BigDecimal invoiced = zero(timeEntryRepository.invoicedOn(project.getId()));
            BigDecimal awaiting = zero(timeEntryRepository.awaitingBillingOn(project.getId()));

            rows.add(profitRow(project, revenue, cost, hours, timeCost, invoiced, awaiting));
            totalRevenue = totalRevenue.add(revenue);
            totalCost = totalCost.add(cost);
            totalHours = totalHours.add(hours);
            totalTimeCost = totalTimeCost.add(timeCost);
            totalInvoiced = totalInvoiced.add(invoiced);
            totalAwaiting = totalAwaiting.add(awaiting);
        }

        ProfitRow total = profitRow(null, totalRevenue, totalCost, totalHours, totalTimeCost,
                totalInvoiced, totalAwaiting);
        return new Profitability(from, to, rows, total, untagged);
    }

    private static ProfitRow profitRow(Project project, BigDecimal revenue, BigDecimal cost,
                                       BigDecimal hours, BigDecimal timeCost,
                                       BigDecimal invoiced, BigDecimal awaiting) {
        BigDecimal margin = revenue.subtract(cost);
        BigDecimal marginPercent = revenue.signum() == 0 ? BigDecimal.ZERO
                : margin.multiply(HUNDRED).divide(revenue.abs(), 1, RoundingMode.HALF_UP);
        BigDecimal contract = project != null && project.isFixedPrice()
                ? zero(project.getFixedPrice()) : invoiced;
        return new ProfitRow(project, revenue, cost, margin, marginPercent, hours, timeCost,
                invoiced, awaiting, contract);
    }

    /** What the tagging screen has and has not reached, so a blank report is not read as no cost. */
    @Transactional(readOnly = true)
    public Coverage coverage(LocalDate from, LocalDate to) {
        Map<Long, Account> accounts = accountsById();
        int tradingLines = 0;
        int taggedLines = 0;
        for (JournalLine line : journalLineRepository.postedLinesBetween(from, to)) {
            Account account = accounts.get(line.getAccountId());
            if (account == null || !isTrading(account.getType())) {
                continue;
            }
            tradingLines++;
            if (line.getProjectId() != null) {
                taggedLines++;
            }
        }
        BigDecimal percent = tradingLines == 0 ? BigDecimal.ZERO
                : new BigDecimal(taggedLines).multiply(HUNDRED)
                        .divide(new BigDecimal(tradingLines), 1, RoundingMode.HALF_UP);
        return new Coverage(tradingLines, taggedLines, tradingLines - taggedLines, percent);
    }

    // ----- Helpers -----

    static boolean isTrading(AccountType type) {
        return type == AccountType.REVENUE || type == AccountType.EXPENSE;
    }

    private static BigDecimal sign(AccountType type, BigDecimal debit, BigDecimal credit) {
        return type == AccountType.EXPENSE || type == AccountType.ASSET
                ? debit.subtract(credit)
                : credit.subtract(debit);
    }

    private static BigDecimal amount(Object value) {
        return value == null ? BigDecimal.ZERO : (BigDecimal) value;
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public record CostLine(Long id, Long entryId, String entryNo, java.time.LocalDate entryDate,
                           String reference, String accountCode, String accountName, String memo,
                           BigDecimal debit, BigDecimal credit, String projectCode) {

        public boolean isTagged() {
            return projectCode != null;
        }
    }

    public record TagResult(Project project, int tagged, int alreadyTagged, int refused) {}

    public record ProfitRow(Project project, BigDecimal ledgerRevenue, BigDecimal ledgerCost,
                            BigDecimal ledgerMargin, BigDecimal ledgerMarginPercent,
                            BigDecimal hours, BigDecimal timeCost, BigDecimal invoiced,
                            BigDecimal awaitingBilling, BigDecimal contractValue) {

        public boolean isLosing() {
            return ledgerMargin.signum() < 0;
        }

        public boolean hasLedgerActivity() {
            return ledgerRevenue.signum() != 0 || ledgerCost.signum() != 0;
        }

        public boolean hasUnbilled() {
            return awaitingBilling.signum() > 0;
        }
    }

    public record Profitability(LocalDate from, LocalDate to, List<ProfitRow> rows,
                                ProfitRow total, int projectsWithNoLedgerCost) {

        public boolean isEmpty() {
            return rows.isEmpty();
        }
    }

    public record Coverage(int tradingLines, int taggedLines, int untaggedLines,
                           BigDecimal taggedPercent) {

        public boolean isComplete() {
            return tradingLines > 0 && untaggedLines == 0;
        }

        public boolean hasNothing() {
            return tradingLines == 0;
        }
    }
}
