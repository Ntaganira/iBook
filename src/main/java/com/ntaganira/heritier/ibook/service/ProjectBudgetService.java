/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : ProjectBudgetService.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : What a job was expected to earn and cost, and how the ledger compares
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.ProjectBudgetForm;
import com.ntaganira.heritier.ibook.entity.Account;
import com.ntaganira.heritier.ibook.entity.Project;
import com.ntaganira.heritier.ibook.entity.ProjectBudget;
import com.ntaganira.heritier.ibook.entity.ProjectBudgetLine;
import com.ntaganira.heritier.ibook.enums.AccountType;
import com.ntaganira.heritier.ibook.enums.BudgetStatus;
import com.ntaganira.heritier.ibook.repository.AccountRepository;
import com.ntaganira.heritier.ibook.repository.ProjectBudgetRepository;
import com.ntaganira.heritier.ibook.repository.ProjectRepository;
import com.ntaganira.heritier.ibook.repository.TimeEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A project budget is what a job was expected to earn and cost, held per account rather than per
 * month: a job runs for as long as it runs, and forcing it onto a calendar year would invent a
 * split of figures nobody planned that way.
 *
 * <p>It is <strong>entirely non-posting</strong>, like the annual budget it sits beside — a plan is
 * not a transaction. Approving only freezes the figures so a comparison measures against something
 * that has stopped moving.
 *
 * <p>Actual comes from <em>tagged</em> ledger lines, the same ones {@link JobCostingService}
 * maintains, so the plan and the outturn are read off the same books. The consequence is stated
 * rather than hidden: a job whose costs nobody has tagged compares against zero and will look
 * triumphantly under budget, which is why every comparison reports how much of the ledger has
 * actually been tagged.
 */
@Service
public class ProjectBudgetService {

    private static final String MODULE = "projects";
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final ProjectBudgetRepository projectBudgetRepository;
    private final ProjectRepository projectRepository;
    private final AccountRepository accountRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final JobCostingService jobCostingService;
    private final AuditService auditService;

    public ProjectBudgetService(ProjectBudgetRepository projectBudgetRepository,
                                ProjectRepository projectRepository,
                                AccountRepository accountRepository,
                                TimeEntryRepository timeEntryRepository,
                                JobCostingService jobCostingService,
                                AuditService auditService) {
        this.projectBudgetRepository = projectBudgetRepository;
        this.projectRepository = projectRepository;
        this.accountRepository = accountRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.jobCostingService = jobCostingService;
        this.auditService = auditService;
    }

    // ----- Queries -----

    @Transactional(readOnly = true)
    public List<ProjectBudget> all() {
        return projectBudgetRepository.findAllByOrderByProjectCodeAsc();
    }

    @Transactional(readOnly = true)
    public ProjectBudget get(Long id) {
        return id == null ? null : projectBudgetRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public ProjectBudget forProject(Long projectId) {
        return projectId == null ? null
                : projectBudgetRepository.findByProjectId(projectId).orElse(null);
    }

    @Transactional(readOnly = true)
    public BudgetSummary summary() {
        return new BudgetSummary(projectBudgetRepository.count(),
                projectBudgetRepository.countByStatus(BudgetStatus.DRAFT),
                projectBudgetRepository.countByStatus(BudgetStatus.APPROVED),
                projectBudgetRepository.countByStatus(BudgetStatus.CLOSED));
    }

    /**
     * The jobs still without a budget, plus whichever one is being edited. A job that already has
     * a budget is left out because a second one would make every variance ambiguous.
     */
    @Transactional(readOnly = true)
    public List<Project> projectsForPicker(Long currentProjectId) {
        List<Project> rows = new ArrayList<>();
        for (Project project : projectRepository.findAllByOrderByCodeAsc()) {
            boolean chosen = currentProjectId != null && currentProjectId.equals(project.getId());
            if (chosen || (!project.isCancelled()
                    && !projectBudgetRepository.existsByProjectId(project.getId()))) {
                rows.add(project);
            }
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<Account> budgetableAccounts() {
        return jobCostingService.costingAccounts();
    }

    // ----- Create / update -----

    @Transactional
    public ProjectBudget save(ProjectBudgetForm form, Long id, String username) {
        Project project = form.getProjectId() == null ? null
                : projectRepository.findById(form.getProjectId()).orElse(null);
        if (project == null) {
            throw new IllegalArgumentException("Choose the job this budget is for");
        }
        if (project.isCancelled()) {
            throw new IllegalStateException("A cancelled project cannot be budgeted");
        }

        ProjectBudget budget;
        if (id == null) {
            if (projectBudgetRepository.existsByProjectId(project.getId())) {
                throw new IllegalArgumentException(
                        "That job already has a budget. Edit the one it has.");
            }
            budget = new ProjectBudget();
            budget.setCreatedBy(username);
            budget.setStatus(BudgetStatus.DRAFT);
        } else {
            budget = projectBudgetRepository.findById(id).orElseThrow();
            if (!budget.isEditable()) {
                throw new IllegalStateException(
                        "An approved budget is frozen. Reopen it before changing the figures.");
            }
            if (!budget.getProjectId().equals(project.getId())
                    && projectBudgetRepository.existsByProjectId(project.getId())) {
                throw new IllegalArgumentException("That job already has a budget");
            }
        }

        if (form.budgetedHoursValue().signum() < 0) {
            throw new IllegalArgumentException("Budgeted hours cannot be negative");
        }

        budget.setProjectId(project.getId());
        budget.setProjectCode(project.getCode());
        budget.setProjectName(project.getName());
        budget.setDescription(trimToNull(form.getDescription()));
        budget.setBudgetedHours(form.budgetedHoursValue());
        budget.setNotes(trimToNull(form.getNotes()));

        budget.getLines().clear();
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        Set<Long> seen = new HashSet<>();
        int order = 0;

        for (ProjectBudgetForm.Line lineForm : form.getLines()) {
            if (lineForm == null || !lineForm.isFilled()) {
                continue;
            }
            Account account = accountRepository.findById(lineForm.getAccountId()).orElse(null);
            if (account == null) {
                continue;
            }
            if (!JobCostingService.isTrading(account.getType())) {
                throw new IllegalArgumentException("Only revenue and expense accounts can be "
                        + "budgeted on a job — " + account.getCode() + " is neither");
            }
            if (!seen.add(account.getId())) {
                throw new IllegalArgumentException(
                        "Account " + account.getCode() + " is on the budget twice");
            }
            BigDecimal amount = lineForm.amountValue();
            if (amount.signum() < 0) {
                throw new IllegalArgumentException("A budgeted figure cannot be negative. Plan a "
                        + "cost as a cost and a revenue as a revenue.");
            }

            ProjectBudgetLine line = ProjectBudgetLine.builder()
                    .accountId(account.getId())
                    .accountCode(account.getCode())
                    .accountName(account.getName())
                    .accountType(account.getType())
                    .amount(amount.setScale(2, RoundingMode.HALF_UP))
                    .notes(trimToNull(lineForm.getNotes()))
                    .sortOrder(order++)
                    .build();
            budget.addLine(line);

            if (account.getType() == AccountType.REVENUE) {
                revenue = revenue.add(amount);
            } else {
                cost = cost.add(amount);
            }
        }

        if (budget.getLines().isEmpty()) {
            throw new IllegalArgumentException("A budget needs at least one account on it");
        }

        budget.setTotalRevenue(revenue.setScale(2, RoundingMode.HALF_UP));
        budget.setTotalCost(cost.setScale(2, RoundingMode.HALF_UP));

        ProjectBudget saved = projectBudgetRepository.save(budget);
        auditService.log(MODULE, id == null ? "CREATE_PROJECT_BUDGET" : "UPDATE_PROJECT_BUDGET",
                "projectBudget#" + saved.getId(), saved.getProjectCode() + " "
                        + saved.getLines().size() + " lines");

        if (form.isApproveNow() && saved.isDraft()) {
            saved = approve(saved.getId(), username);
        }
        return saved;
    }

    // ----- Lifecycle -----

    @Transactional
    public ProjectBudget approve(Long id, String username) {
        ProjectBudget budget = projectBudgetRepository.findById(id).orElseThrow();
        if (!budget.isDraft()) {
            throw new IllegalStateException("Only a draft budget can be approved");
        }
        budget.setStatus(BudgetStatus.APPROVED);
        budget.setApprovedAt(LocalDateTime.now());
        budget.setApprovedBy(username);
        return log(projectBudgetRepository.save(budget), "APPROVE_PROJECT_BUDGET");
    }

    @Transactional
    public ProjectBudget reopen(Long id) {
        ProjectBudget budget = projectBudgetRepository.findById(id).orElseThrow();
        if (budget.isDraft()) {
            throw new IllegalStateException("This budget is already a draft");
        }
        budget.setStatus(BudgetStatus.DRAFT);
        budget.setApprovedAt(null);
        budget.setApprovedBy(null);
        return log(projectBudgetRepository.save(budget), "REOPEN_PROJECT_BUDGET");
    }

    @Transactional
    public ProjectBudget close(Long id) {
        ProjectBudget budget = projectBudgetRepository.findById(id).orElseThrow();
        if (budget.isClosed()) {
            throw new IllegalStateException("This budget is already closed");
        }
        budget.setStatus(BudgetStatus.CLOSED);
        return log(projectBudgetRepository.save(budget), "CLOSE_PROJECT_BUDGET");
    }

    @Transactional
    public void delete(Long id) {
        ProjectBudget budget = projectBudgetRepository.findById(id).orElse(null);
        if (budget == null) {
            return;
        }
        if (!budget.isDraft()) {
            throw new IllegalStateException(
                    "Only a draft budget can be deleted. Close an approved one instead.");
        }
        projectBudgetRepository.delete(budget);
        auditService.log(MODULE, "DELETE_PROJECT_BUDGET", "projectBudget#" + id,
                budget.getProjectCode());
    }

    // ----- Budget against what the ledger did -----

    /**
     * Compares the plan with the tagged ledger over a date range.
     *
     * <p>Variance is reported as <em>favourable or adverse</em> rather than raw arithmetic, the
     * same way {@code /budgets/vs-actual} does it: earning more than planned and spending less than
     * planned are both good and carry opposite signs, so one subtraction for both would put a
     * windfall and an overspend in the same column.
     */
    @Transactional(readOnly = true)
    public Comparison compare(ProjectBudget budget, LocalDate from, LocalDate to) {
        if (budget == null) {
            return null;
        }
        Map<Long, BigDecimal> actuals =
                jobCostingService.signedTotalsForProject(budget.getProjectId(), from, to);
        Map<Long, Account> accounts = jobCostingService.accountsById();

        List<ComparisonRow> revenue = new ArrayList<>();
        List<ComparisonRow> cost = new ArrayList<>();
        BigDecimal budgetRevenue = BigDecimal.ZERO;
        BigDecimal actualRevenue = BigDecimal.ZERO;
        BigDecimal budgetCost = BigDecimal.ZERO;
        BigDecimal actualCost = BigDecimal.ZERO;
        Set<Long> named = new HashSet<>();

        for (ProjectBudgetLine line : budget.getLines()) {
            named.add(line.getAccountId());
            BigDecimal budgeted = line.getAmountValue();
            BigDecimal actual = zero(actuals.get(line.getAccountId()));
            boolean revenueLine = line.isRevenue();
            ComparisonRow row = row(line.getAccountCode(), line.getAccountName(),
                    line.getAccountId(), budgeted, actual, revenueLine);
            if (revenueLine) {
                revenue.add(row);
                budgetRevenue = budgetRevenue.add(budgeted);
                actualRevenue = actualRevenue.add(actual);
            } else {
                cost.add(row);
                budgetCost = budgetCost.add(budgeted);
                actualCost = actualCost.add(actual);
            }
        }

        List<ComparisonRow> unbudgeted = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : actuals.entrySet()) {
            if (named.contains(entry.getKey()) || entry.getValue().signum() == 0) {
                continue;
            }
            Account account = accounts.get(entry.getKey());
            if (account == null) {
                continue;
            }
            boolean revenueLine = account.getType() == AccountType.REVENUE;
            unbudgeted.add(row(account.getCode(), account.getName(), account.getId(),
                    BigDecimal.ZERO, entry.getValue(), revenueLine));
        }

        ComparisonRow revenueTotal = row(null, null, null, budgetRevenue, actualRevenue, true);
        ComparisonRow costTotal = row(null, null, null, budgetCost, actualCost, false);
        ComparisonRow marginTotal = row(null, null, null,
                budgetRevenue.subtract(budgetCost), actualRevenue.subtract(actualCost), true);

        Project project = projectRepository.findById(budget.getProjectId()).orElse(null);
        BigDecimal hours = zero(timeEntryRepository.hoursOn(budget.getProjectId()));
        BigDecimal budgetedHours = zero(budget.getBudgetedHours());
        BigDecimal hoursPercent = budgetedHours.signum() == 0 ? BigDecimal.ZERO
                : hours.multiply(HUNDRED).divide(budgetedHours, 1, RoundingMode.HALF_UP);

        return new Comparison(budget, project, from, to, revenue, cost, revenueTotal, costTotal,
                marginTotal, unbudgeted, budgetedHours, hours, hoursPercent,
                jobCostingService.coverage(from, to));
    }

    private static ComparisonRow row(String code, String name, Long accountId,
                                     BigDecimal budgeted, BigDecimal actual, boolean revenueLine) {
        BigDecimal variance = revenueLine
                ? actual.subtract(budgeted)
                : budgeted.subtract(actual);
        BigDecimal percent = budgeted.signum() == 0 ? BigDecimal.ZERO
                : variance.multiply(HUNDRED).divide(budgeted.abs(), 1, RoundingMode.HALF_UP);
        return new ComparisonRow(code, name, accountId, budgeted, actual, variance, percent,
                revenueLine);
    }

    /** One row per budget for the list page, so a job's position is visible without opening it. */
    @Transactional(readOnly = true)
    public List<ListRow> listRows(LocalDate from, LocalDate to) {
        List<ListRow> rows = new ArrayList<>();
        Map<Long, Project> projects = new LinkedHashMap<>();
        for (Project project : projectRepository.findAllByOrderByCodeAsc()) {
            projects.put(project.getId(), project);
        }
        for (ProjectBudget budget : projectBudgetRepository.findAllByOrderByProjectCodeAsc()) {
            Map<Long, BigDecimal> actuals =
                    jobCostingService.signedTotalsForProject(budget.getProjectId(), from, to);
            BigDecimal actualRevenue = BigDecimal.ZERO;
            BigDecimal actualCost = BigDecimal.ZERO;
            Map<Long, Account> accounts = jobCostingService.accountsById();
            for (Map.Entry<Long, BigDecimal> entry : actuals.entrySet()) {
                Account account = accounts.get(entry.getKey());
                if (account == null) {
                    continue;
                }
                if (account.getType() == AccountType.REVENUE) {
                    actualRevenue = actualRevenue.add(entry.getValue());
                } else {
                    actualCost = actualCost.add(entry.getValue());
                }
            }
            rows.add(new ListRow(budget, projects.get(budget.getProjectId()),
                    actualRevenue, actualCost,
                    row(null, null, null, zero(budget.getTotalRevenue()), actualRevenue, true),
                    row(null, null, null, zero(budget.getTotalCost()), actualCost, false)));
        }
        return rows;
    }

    // ----- Helpers -----

    private ProjectBudget log(ProjectBudget budget, String action) {
        auditService.log(MODULE, action, "projectBudget#" + budget.getId(),
                budget.getProjectCode() + " " + budget.getProjectName());
        return budget;
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record ComparisonRow(String accountCode, String accountName, Long accountId,
                                BigDecimal budgeted, BigDecimal actual, BigDecimal variance,
                                BigDecimal variancePercent, boolean revenueLine) {

        public boolean isFavourable() {
            return variance.signum() > 0;
        }

        public boolean isAdverse() {
            return variance.signum() < 0;
        }

        public BigDecimal varianceMagnitude() {
            return variance.abs();
        }
    }

    public record Comparison(ProjectBudget budget, Project project, LocalDate from, LocalDate to,
                             List<ComparisonRow> revenue, List<ComparisonRow> cost,
                             ComparisonRow revenueTotal, ComparisonRow costTotal,
                             ComparisonRow marginTotal, List<ComparisonRow> unbudgeted,
                             BigDecimal budgetedHours, BigDecimal actualHours,
                             BigDecimal hoursPercent, JobCostingService.Coverage coverage) {

        public boolean hasUnbudgeted() {
            return !unbudgeted.isEmpty();
        }

        public boolean isOverHours() {
            return budgetedHours.signum() > 0 && actualHours.compareTo(budgetedHours) > 0;
        }
    }

    public record ListRow(ProjectBudget budget, Project project, BigDecimal actualRevenue,
                          BigDecimal actualCost, ComparisonRow revenueVariance,
                          ComparisonRow costVariance) {

        public BigDecimal actualMargin() {
            return actualRevenue.subtract(actualCost);
        }
    }

    public record BudgetSummary(long all, long draft, long approved, long closed) {}
}
