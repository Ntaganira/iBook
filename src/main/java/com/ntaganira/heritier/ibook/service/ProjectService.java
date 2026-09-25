/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : ProjectService.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Projects, their lifecycle and what the booked time says about them
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.ProjectForm;
import com.ntaganira.heritier.ibook.entity.Company;
import com.ntaganira.heritier.ibook.entity.Customer;
import com.ntaganira.heritier.ibook.entity.Project;
import com.ntaganira.heritier.ibook.entity.NumberingSequence;
import com.ntaganira.heritier.ibook.enums.ProjectBillingType;
import com.ntaganira.heritier.ibook.enums.ProjectStatus;
import com.ntaganira.heritier.ibook.repository.CompanyRepository;
import com.ntaganira.heritier.ibook.repository.CustomerRepository;
import com.ntaganira.heritier.ibook.repository.NumberingSequenceRepository;
import com.ntaganira.heritier.ibook.repository.ProjectRepository;
import com.ntaganira.heritier.ibook.repository.TimeEntryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ProjectService {

    private static final String MODULE = "projects";
    private static final String DOC_TYPE = "PROJECT";
    private static final String FALLBACK_PREFIX = "PRJ-";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ProjectRepository projectRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final CustomerRepository customerRepository;
    private final CompanyRepository companyRepository;
    private final NumberingSequenceRepository numberingSequenceRepository;
    private final AuditService auditService;

    public ProjectService(ProjectRepository projectRepository,
                          TimeEntryRepository timeEntryRepository,
                          CustomerRepository customerRepository,
                          CompanyRepository companyRepository,
                          NumberingSequenceRepository numberingSequenceRepository,
                          AuditService auditService) {
        this.projectRepository = projectRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.customerRepository = customerRepository;
        this.companyRepository = companyRepository;
        this.numberingSequenceRepository = numberingSequenceRepository;
        this.auditService = auditService;
    }

    // ----- Queries -----

    @Transactional(readOnly = true)
    public Page<Project> list(String q, String status, Long customerId, Pageable pageable) {
        return projectRepository.search(trimToNull(q), parseStatus(status), customerId, pageable);
    }

    @Transactional(readOnly = true)
    public Project get(Long id) {
        return id == null ? null : projectRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Project> all() {
        return projectRepository.findAllByOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public List<Project> openForTime() {
        return projectRepository.openForTime();
    }

    /**
     * The jobs the timesheet form may offer, plus whatever the entry being edited already carries.
     * A job that closes would otherwise drop out of the picker, and the next save of an unrelated
     * field would silently move the entry onto a different project.
     */
    @Transactional(readOnly = true)
    public List<Project> projectsForPicker(Long currentId) {
        List<Project> rows = new ArrayList<>(projectRepository.openForTime());
        if (currentId != null && rows.stream().noneMatch(p -> currentId.equals(p.getId()))) {
            projectRepository.findById(currentId).ifPresent(rows::add);
        }
        return rows;
    }

    /** Same rule for customers: an inactive one stays on the job that already names it. */
    @Transactional(readOnly = true)
    public List<Customer> customersForPicker(Long currentId) {
        List<Customer> rows = new ArrayList<>(customerRepository.findByActiveTrueOrderByNameAsc());
        if (currentId != null && rows.stream().noneMatch(c -> currentId.equals(c.getId()))) {
            customerRepository.findById(currentId).ifPresent(rows::add);
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public String baseCurrency() {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElse(null);
        return company == null || company.getCurrencyCode() == null ? "RWF" : company.getCurrencyCode();
    }

    @Transactional(readOnly = true)
    public boolean codeExists(String code, Long id) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return id == null
                ? projectRepository.existsByCodeIgnoreCase(code.trim())
                : projectRepository.existsByCodeIgnoreCaseAndIdNot(code.trim(), id);
    }

    @Transactional(readOnly = true)
    public boolean nameExists(String name, Long id) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return id == null
                ? projectRepository.existsByNameIgnoreCase(name.trim())
                : projectRepository.existsByNameIgnoreCaseAndIdNot(name.trim(), id);
    }

    @Transactional(readOnly = true)
    public ProjectSummary summary() {
        return new ProjectSummary(
                projectRepository.count(),
                projectRepository.countByStatus(ProjectStatus.DRAFT),
                projectRepository.countByStatus(ProjectStatus.ACTIVE),
                projectRepository.countByStatus(ProjectStatus.ON_HOLD),
                projectRepository.countByStatus(ProjectStatus.COMPLETED),
                projectRepository.countByStatus(ProjectStatus.CANCELLED));
    }

    /**
     * What the booked time says a job has cost and earned.
     *
     * <p>Cost here is hours times a cost rate, which is a <em>management</em> figure and not a
     * ledger balance: the wages behind those hours reach the books through payroll, so nothing in
     * this module posts them a second time. Revenue is only what has actually been invoiced, so a
     * job shows no income until somebody has billed for it — hours sitting approved but unbilled
     * are reported separately rather than counted as earnings nobody has asked for yet.
     */
    @Transactional(readOnly = true)
    public Position positionOf(Project project) {
        if (project == null) {
            return null;
        }
        BigDecimal hours = zero(timeEntryRepository.hoursOn(project.getId()));
        BigDecimal cost = zero(timeEntryRepository.costOn(project.getId()));
        BigDecimal invoiced = zero(timeEntryRepository.invoicedOn(project.getId()));
        BigDecimal awaiting = zero(timeEntryRepository.awaitingBillingOn(project.getId()));
        BigDecimal estimated = zero(project.getEstimatedHours());

        BigDecimal earned = project.isFixedPrice() ? zero(project.getFixedPrice()) : invoiced;
        BigDecimal margin = earned.subtract(cost);
        BigDecimal marginPercent = earned.signum() == 0 ? BigDecimal.ZERO
                : margin.multiply(new BigDecimal("100"))
                        .divide(earned.abs(), 1, RoundingMode.HALF_UP);
        BigDecimal hoursPercent = estimated.signum() == 0 ? BigDecimal.ZERO
                : hours.multiply(new BigDecimal("100"))
                        .divide(estimated, 1, RoundingMode.HALF_UP);

        return new Position(project, hours, estimated, hoursPercent, cost, invoiced, awaiting,
                earned, margin, marginPercent, timeEntryRepository.countByProjectId(project.getId()));
    }

    // ----- Create / update -----

    @Transactional
    public Project save(ProjectForm form, Long id, String username) {
        Project project;
        if (id == null) {
            project = new Project();
            project.setCreatedBy(username);
            project.setStatus(ProjectStatus.DRAFT);
        } else {
            project = projectRepository.findById(id).orElseThrow();
            if (project.isCancelled()) {
                throw new IllegalStateException("A cancelled project cannot be edited");
            }
        }

        if (trimToNull(form.name()) == null) {
            throw new IllegalArgumentException("A project needs a name");
        }
        String code = trimToNull(form.code());
        if (code == null) {
            code = id == null ? nextCode() : project.getCode();
        }
        if (form.endDate() != null && form.startDate() != null
                && form.endDate().isBefore(form.startDate())) {
            throw new IllegalArgumentException("The end date falls before the start date");
        }

        ProjectBillingType billing = parseBilling(form.billingType());
        Customer customer = form.customerId() == null ? null
                : customerRepository.findById(form.customerId()).orElse(null);
        if (billing != ProjectBillingType.NON_BILLABLE && customer == null) {
            throw new IllegalArgumentException(
                    "A project that bills somebody needs a customer on it");
        }
        if (form.defaultBillRateValue().signum() < 0 || form.defaultCostRateValue().signum() < 0
                || form.fixedPriceValue().signum() < 0 || form.estimatedHoursValue().signum() < 0) {
            throw new IllegalArgumentException("Rates, prices and hours cannot be negative");
        }

        project.setCode(code);
        project.setName(form.name().trim());
        project.setCustomerId(customer == null ? null : customer.getId());
        project.setCustomerName(customer == null ? null : customer.getName());
        project.setDescription(trimToNull(form.description()));
        project.setBillingType(billing);
        project.setStartDate(form.startDate());
        project.setEndDate(form.endDate());
        project.setManager(trimToNull(form.manager()));
        project.setFixedPrice(billing == ProjectBillingType.FIXED_PRICE
                ? form.fixedPriceValue() : BigDecimal.ZERO);
        project.setDefaultBillRate(billing.isHourlyBillable()
                ? form.defaultBillRateValue() : BigDecimal.ZERO);
        project.setDefaultCostRate(form.defaultCostRateValue());
        project.setEstimatedHours(form.estimatedHoursValue());
        project.setCurrencyCode(trimToNull(form.currencyCode()) == null
                ? baseCurrency() : form.currencyCode().trim());
        project.setNotes(trimToNull(form.notes()));

        Project saved = projectRepository.save(project);
        renameOnTimeEntries(saved);
        auditService.log(MODULE, id == null ? "CREATE_PROJECT" : "UPDATE_PROJECT",
                "project#" + saved.getId(), saved.getCode() + " " + saved.getName());

        if (form.activateNowValue() && saved.isDraft()) {
            saved = activate(saved.getId());
        }
        return saved;
    }

    /**
     * Entries carry the code, name and customer they were booked under. Renaming the job updates
     * them, because a timesheet listing a name that no longer exists anywhere is just confusing —
     * unlike a completed asset transfer, a time entry is not a record of what the job was called
     * on the day.
     */
    private void renameOnTimeEntries(Project project) {
        timeEntryRepository.findByProjectIdOrderByWorkDateDescIdDesc(project.getId())
                .forEach(entry -> {
                    entry.setProjectCode(project.getCode());
                    entry.setProjectName(project.getName());
                    if (!entry.isInvoiced()) {
                        entry.setCustomerId(project.getCustomerId());
                        entry.setCustomerName(project.getCustomerName());
                    }
                    timeEntryRepository.save(entry);
                });
    }

    // ----- Lifecycle -----

    @Transactional
    public Project activate(Long id) {
        Project project = projectRepository.findById(id).orElseThrow();
        if (project.isCancelled()) {
            throw new IllegalStateException("A cancelled project cannot be started");
        }
        if (project.isRunning()) {
            throw new IllegalStateException("This project is already running");
        }
        project.setStatus(ProjectStatus.ACTIVE);
        project.setCompletedAt(null);
        return log(projectRepository.save(project), "ACTIVATE_PROJECT");
    }

    @Transactional
    public Project hold(Long id) {
        Project project = projectRepository.findById(id).orElseThrow();
        if (!project.isRunning()) {
            throw new IllegalStateException("Only a running project can be put on hold");
        }
        project.setStatus(ProjectStatus.ON_HOLD);
        return log(projectRepository.save(project), "HOLD_PROJECT");
    }

    /**
     * Completing closes the job to new time but deliberately does not care whether every approved
     * hour has been billed. Chasing that is a decision for whoever runs the job, and refusing to
     * close would only leave stale jobs open; the unbilled figure stays visible on the page.
     */
    @Transactional
    public Project complete(Long id) {
        Project project = projectRepository.findById(id).orElseThrow();
        if (project.isCancelled()) {
            throw new IllegalStateException("A cancelled project cannot be completed");
        }
        if (project.isCompleted()) {
            throw new IllegalStateException("This project is already complete");
        }
        project.setStatus(ProjectStatus.COMPLETED);
        project.setCompletedAt(LocalDateTime.now());
        return log(projectRepository.save(project), "COMPLETE_PROJECT");
    }

    @Transactional
    public Project reopen(Long id) {
        Project project = projectRepository.findById(id).orElseThrow();
        if (!project.isCompleted() && !project.isCancelled() && !project.isOnHold()) {
            throw new IllegalStateException("This project is already open");
        }
        project.setStatus(ProjectStatus.ACTIVE);
        project.setCompletedAt(null);
        return log(projectRepository.save(project), "REOPEN_PROJECT");
    }

    /**
     * Cancelling closes the job without pretending the work was finished. Time already booked is
     * left alone: hours somebody worked are a fact, and invoiced hours are evidence for a document
     * the customer has been sent.
     */
    @Transactional
    public Project cancel(Long id, String reason) {
        Project project = projectRepository.findById(id).orElseThrow();
        if (project.isCancelled()) {
            throw new IllegalStateException("This project is already cancelled");
        }
        project.setStatus(ProjectStatus.CANCELLED);
        project.setNotes(appendReason(project.getNotes(), reason));
        return log(projectRepository.save(project), "CANCEL_PROJECT");
    }

    @Transactional
    public void delete(Long id) {
        Project project = projectRepository.findById(id).orElse(null);
        if (project == null) {
            return;
        }
        long booked = timeEntryRepository.countByProjectId(id);
        if (booked > 0) {
            throw new IllegalArgumentException("This project has " + booked
                    + " time entries against it. Cancel it instead of deleting it.");
        }
        if (!project.isDraft()) {
            throw new IllegalStateException("Only a project still in draft can be deleted");
        }
        projectRepository.delete(project);
        auditService.log(MODULE, "DELETE_PROJECT", "project#" + id,
                project.getCode() + " " + project.getName());
    }

    // ----- Helpers -----

    private Project log(Project project, String action) {
        auditService.log(MODULE, action, "project#" + project.getId(),
                project.getCode() + " " + project.getName());
        return project;
    }

    private static String appendReason(String notes, String reason) {
        String trimmed = trimToNull(reason);
        if (trimmed == null) {
            return notes;
        }
        return notes == null || notes.isBlank() ? trimmed : notes + " · " + trimmed;
    }

    @Transactional(readOnly = true)
    public String previewNextCode() {
        NumberingSequence seq = numberingSequenceRepository.findByDocType(DOC_TYPE).orElse(null);
        return seq == null ? FALLBACK_PREFIX + "0001" : seq.previewNext();
    }

    private String nextCode() {
        NumberingSequence seq = numberingSequenceRepository.findByDocType(DOC_TYPE).orElse(null);
        if (seq != null) {
            long next = seq.getNextNumber();
            String prefix = seq.getPrefix() == null ? FALLBACK_PREFIX : seq.getPrefix();
            String suffix = seq.getSuffix() == null ? "" : seq.getSuffix();
            int padding = seq.getPadding() == 0 ? 4 : seq.getPadding();
            String code = prefix + String.format("%0" + padding + "d", next) + suffix;
            seq.setNextNumber(next + 1);
            numberingSequenceRepository.save(seq);
            return code;
        }
        return FALLBACK_PREFIX + LocalDate.now().getYear() + "-"
                + String.format("%05d", RANDOM.nextInt(100000));
    }

    public static ProjectStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ProjectStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static ProjectBillingType parseBilling(String billingType) {
        if (billingType == null || billingType.isBlank()) {
            return ProjectBillingType.TIME_AND_MATERIALS;
        }
        try {
            return ProjectBillingType.valueOf(billingType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ProjectBillingType.TIME_AND_MATERIALS;
        }
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

    public record Position(Project project, BigDecimal hours, BigDecimal estimatedHours,
                           BigDecimal hoursPercent, BigDecimal cost, BigDecimal invoiced,
                           BigDecimal awaitingBilling, BigDecimal earned, BigDecimal margin,
                           BigDecimal marginPercent, long entryCount) {

        public boolean isOverEstimate() {
            return estimatedHours.signum() > 0 && hours.compareTo(estimatedHours) > 0;
        }

        public boolean isLosing() {
            return margin.signum() < 0;
        }

        public boolean hasUnbilled() {
            return awaitingBilling.signum() > 0;
        }
    }

    public record ProjectSummary(long all, long draft, long active, long onHold,
                                 long completed, long cancelled) {}
}
