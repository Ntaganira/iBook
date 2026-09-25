/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : TimeEntryService.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Booked time, its approval, and turning approved hours into a draft invoice
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.InvoiceForm;
import com.ntaganira.heritier.ibook.dto.TimeBillingForm;
import com.ntaganira.heritier.ibook.dto.TimeEntryForm;
import com.ntaganira.heritier.ibook.entity.Invoice;
import com.ntaganira.heritier.ibook.entity.Project;
import com.ntaganira.heritier.ibook.entity.TimeEntry;
import com.ntaganira.heritier.ibook.enums.TimeEntryStatus;
import com.ntaganira.heritier.ibook.repository.ProjectRepository;
import com.ntaganira.heritier.ibook.repository.TimeEntryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Time is <strong>never posted</strong>. Booking hours records what somebody did; the wages behind
 * those hours already reach the ledger through payroll, so accruing them again from a timesheet
 * would count the same cost twice. Time becomes money in exactly one place — an invoice raised
 * from approved, billable hours — and even then nothing reaches the ledger until that invoice is
 * posted, because it is created as a draft through {@link InvoiceService} like every other
 * conversion in this codebase.
 */
@Service
public class TimeEntryService {

    private static final String MODULE = "timesheets";
    private static final BigDecimal MAX_HOURS_PER_DAY = new BigDecimal("24");

    private final TimeEntryRepository timeEntryRepository;
    private final ProjectRepository projectRepository;
    private final InvoiceService invoiceService;
    private final AuditService auditService;

    public TimeEntryService(TimeEntryRepository timeEntryRepository,
                            ProjectRepository projectRepository,
                            InvoiceService invoiceService,
                            AuditService auditService) {
        this.timeEntryRepository = timeEntryRepository;
        this.projectRepository = projectRepository;
        this.invoiceService = invoiceService;
        this.auditService = auditService;
    }

    // ----- Queries -----

    @Transactional(readOnly = true)
    public Page<TimeEntry> list(String q, String status, Long projectId, String person,
                                LocalDate from, LocalDate to, Pageable pageable) {
        return timeEntryRepository.search(trimToNull(q), parseStatus(status), projectId,
                trimToNull(person), from, to, pageable);
    }

    @Transactional(readOnly = true)
    public TimeEntry get(Long id) {
        return id == null ? null : timeEntryRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<TimeEntry> forProject(Long projectId) {
        return timeEntryRepository.findByProjectIdOrderByWorkDateDescIdDesc(projectId);
    }

    @Transactional(readOnly = true)
    public List<String> people() {
        return timeEntryRepository.distinctPeople();
    }

    @Transactional(readOnly = true)
    public TimeSummary summary() {
        return new TimeSummary(
                timeEntryRepository.countByStatus(TimeEntryStatus.DRAFT),
                timeEntryRepository.countByStatus(TimeEntryStatus.APPROVED),
                timeEntryRepository.countByStatus(TimeEntryStatus.REJECTED),
                timeEntryRepository.countByStatus(TimeEntryStatus.INVOICED),
                zero(timeEntryRepository.hoursByStatus(TimeEntryStatus.DRAFT)),
                zero(timeEntryRepository.hoursByStatus(TimeEntryStatus.APPROVED)));
    }

    // ----- Create / update -----

    @Transactional
    public TimeEntry save(TimeEntryForm form, Long id, String username) {
        TimeEntry entry;
        if (id == null) {
            entry = new TimeEntry();
            entry.setCreatedBy(username);
            entry.setStatus(TimeEntryStatus.DRAFT);
        } else {
            entry = timeEntryRepository.findById(id).orElseThrow();
            if (!entry.isEditable()) {
                throw new IllegalStateException(
                        "These hours are on invoice " + entry.getInvoiceNo() + " and cannot be changed");
            }
        }

        if (form.projectId() == null) {
            throw new IllegalArgumentException("Choose the project the time was spent on");
        }
        Project project = projectRepository.findById(form.projectId()).orElseThrow(
                () -> new IllegalArgumentException("That project no longer exists"));
        boolean sameProject = form.projectId().equals(entry.getProjectId());
        if (!project.isOpenForTime() && !sameProject) {
            throw new IllegalArgumentException(project.getDisplayName()
                    + " is " + project.getStatus().name().toLowerCase(Locale.ROOT).replace('_', ' ')
                    + " and is not taking time");
        }
        if (trimToNull(form.person()) == null) {
            throw new IllegalArgumentException("Say who did the work");
        }
        if (form.workDate() == null) {
            throw new IllegalArgumentException("A time entry needs the day it was worked");
        }
        BigDecimal hours = form.hoursValue();
        if (hours.signum() <= 0) {
            throw new IllegalArgumentException("Booked time has to be more than zero hours");
        }
        if (hours.compareTo(MAX_HOURS_PER_DAY) > 0) {
            throw new IllegalArgumentException("A day holds 24 hours, not " + hours.toPlainString());
        }

        /*
         * A job that does not bill by the hour cannot produce billable time whatever the box says:
         * a fixed price is already agreed, so charging its hours as well would bill twice.
         */
        boolean billable = form.billableValue() && project.isHourlyBillable();
        BigDecimal billRate = billable
                ? orDefault(form.billRate(), project.getDefaultBillRate())
                : BigDecimal.ZERO;
        BigDecimal costRate = orDefault(form.costRate(), project.getDefaultCostRate());
        if (billRate.signum() < 0 || costRate.signum() < 0) {
            throw new IllegalArgumentException("A rate cannot be negative");
        }

        entry.setProjectId(project.getId());
        entry.setProjectCode(project.getCode());
        entry.setProjectName(project.getName());
        entry.setCustomerId(project.getCustomerId());
        entry.setCustomerName(project.getCustomerName());
        entry.setPerson(form.person().trim());
        entry.setWorkDate(form.workDate());
        entry.setHours(hours);
        entry.setTask(trimToNull(form.task()));
        entry.setDescription(trimToNull(form.description()));
        entry.setBillable(billable);
        entry.setBillRate(billRate);
        entry.setCostRate(costRate);
        entry.recalculate();

        // Editing rejected time puts it back in the queue rather than leaving it refused.
        if (entry.isRejected()) {
            entry.setStatus(TimeEntryStatus.DRAFT);
            entry.setRejectedReason(null);
        }

        TimeEntry saved = timeEntryRepository.save(entry);
        auditService.log(MODULE, id == null ? "CREATE_TIME" : "UPDATE_TIME",
                "timeEntry#" + saved.getId(),
                saved.getProjectCode() + " " + saved.getPerson() + " " + saved.getHours() + "h");

        if (form.approveNowValue() && saved.isDraft()) {
            saved = approve(saved.getId(), username);
        }
        return saved;
    }

    // ----- Approval -----

    @Transactional
    public TimeEntry approve(Long id, String username) {
        TimeEntry entry = timeEntryRepository.findById(id).orElseThrow();
        if (entry.isInvoiced()) {
            throw new IllegalStateException("These hours have already been invoiced");
        }
        if (entry.isApproved()) {
            throw new IllegalStateException("These hours are already approved");
        }
        entry.setStatus(TimeEntryStatus.APPROVED);
        entry.setApprovedAt(LocalDateTime.now());
        entry.setApprovedBy(username);
        entry.setRejectedReason(null);
        return log(timeEntryRepository.save(entry), "APPROVE_TIME");
    }

    @Transactional
    public TimeEntry reject(Long id, String reason) {
        TimeEntry entry = timeEntryRepository.findById(id).orElseThrow();
        if (entry.isInvoiced()) {
            throw new IllegalStateException("Invoiced hours cannot be rejected");
        }
        entry.setStatus(TimeEntryStatus.REJECTED);
        entry.setApprovedAt(null);
        entry.setApprovedBy(null);
        entry.setRejectedReason(trimToNull(reason));
        return log(timeEntryRepository.save(entry), "REJECT_TIME");
    }

    @Transactional
    public TimeEntry reopen(Long id) {
        TimeEntry entry = timeEntryRepository.findById(id).orElseThrow();
        if (entry.isInvoiced()) {
            throw new IllegalStateException(
                    "These hours are on invoice " + entry.getInvoiceNo()
                            + ". Void that invoice before changing them.");
        }
        if (entry.isDraft()) {
            throw new IllegalStateException("These hours are already a draft");
        }
        entry.setStatus(TimeEntryStatus.DRAFT);
        entry.setApprovedAt(null);
        entry.setApprovedBy(null);
        entry.setRejectedReason(null);
        return log(timeEntryRepository.save(entry), "REOPEN_TIME");
    }

    @Transactional
    public void delete(Long id) {
        TimeEntry entry = timeEntryRepository.findById(id).orElse(null);
        if (entry == null) {
            return;
        }
        if (entry.isInvoiced()) {
            throw new IllegalStateException(
                    "These hours are on invoice " + entry.getInvoiceNo() + " and cannot be deleted");
        }
        timeEntryRepository.delete(entry);
        auditService.log(MODULE, "DELETE_TIME", "timeEntry#" + id,
                entry.getProjectCode() + " " + entry.getPerson() + " " + entry.getHours() + "h");
    }

    @Transactional
    public int approveAll(List<Long> ids, String username) {
        int done = 0;
        for (Long id : ids == null ? List.<Long>of() : ids) {
            TimeEntry entry = timeEntryRepository.findById(id).orElse(null);
            if (entry != null && entry.isDraft()) {
                approve(id, username);
                done++;
            }
        }
        return done;
    }

    // ----- Billing -----

    @Transactional(readOnly = true)
    public BillableView readyToBill(Long customerId, Long projectId,
                                    LocalDate from, LocalDate to) {
        List<TimeEntry> rows = timeEntryRepository.readyToBill(customerId, projectId, from, to);
        Map<Long, BillableGroup> byCustomer = new LinkedHashMap<>();
        for (TimeEntry entry : rows) {
            BillableGroup group = byCustomer.computeIfAbsent(entry.getCustomerId(),
                    key -> new BillableGroup(key, entry.getCustomerName(), new ArrayList<>()));
            group.entries().add(entry);
        }
        List<BillableGroup> groups = new ArrayList<>(byCustomer.values());
        BigDecimal hours = BigDecimal.ZERO;
        BigDecimal amount = BigDecimal.ZERO;
        for (BillableGroup group : groups) {
            hours = hours.add(group.hours());
            amount = amount.add(group.amount());
        }
        return new BillableView(groups, hours, amount);
    }

    /**
     * Turns the chosen hours into draft invoices, one per customer, by replaying them through
     * {@link InvoiceService} so numbering, tax resolution and totals stay in one place. Nothing
     * reaches the ledger here — the invoice is raised as a draft and posts only when somebody posts
     * it.
     *
     * <p>Every entry is re-read and re-checked inside the transaction rather than trusted from the
     * page that listed it. A tick box submitted from a stale screen is otherwise enough to bill
     * hours that have since been rejected, reopened, or billed by somebody else a minute earlier.
     */
    @Transactional
    public BillingResult bill(TimeBillingForm form, String username) {
        List<Long> ids = form.entryIdsValue();
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("Tick the hours to be billed first");
        }

        Map<Long, List<TimeEntry>> byCustomer = new LinkedHashMap<>();
        int skipped = 0;
        for (Long id : ids) {
            TimeEntry entry = timeEntryRepository.findById(id).orElse(null);
            if (entry == null || !entry.isReadyToBill()) {
                skipped++;
                continue;
            }
            Project project = projectRepository.findById(entry.getProjectId()).orElse(null);
            if (project == null || !project.isHourlyBillable()) {
                skipped++;
                continue;
            }
            byCustomer.computeIfAbsent(entry.getCustomerId(), key -> new ArrayList<>()).add(entry);
        }
        if (byCustomer.isEmpty()) {
            throw new IllegalArgumentException(
                    "None of those hours can be billed any more. Reload the page and try again.");
        }

        LocalDate issue = form.issueDate() == null ? LocalDate.now() : form.issueDate();
        LocalDate due = form.dueDate() == null ? issue.plusDays(30) : form.dueDate();
        if (due.isBefore(issue)) {
            throw new IllegalArgumentException("The due date falls before the issue date");
        }

        List<Invoice> raised = new ArrayList<>();
        int billedEntries = 0;
        BigDecimal billedAmount = BigDecimal.ZERO;

        for (Map.Entry<Long, List<TimeEntry>> row : byCustomer.entrySet()) {
            List<TimeEntry> entries = row.getValue();
            List<InvoiceLineDraft> drafts = form.oneLinePerProjectValue()
                    ? rollUpByProjectAndRate(entries)
                    : oneLinePerEntry(entries);

            InvoiceForm invoiceForm = new InvoiceForm();
            invoiceForm.setCustomerId(row.getKey());
            invoiceForm.setIssueDate(issue);
            invoiceForm.setDueDate(due);
            invoiceForm.setReference(trimToNull(form.reference()));
            invoiceForm.setCustomerMessage(trimToNull(form.customerMessage()));
            invoiceForm.setPostNow(false);

            for (int i = 0; i < drafts.size(); i++) {
                InvoiceLineDraft draft = drafts.get(i);
                InvoiceForm.Line line = invoiceForm.getLines().get(i);
                line.setDescription(draft.description());
                line.setQuantity(draft.hours());
                line.setUnitPrice(draft.rate());
                line.setTaxRateId(form.taxRateId());
            }

            Invoice invoice = invoiceService.saveInvoice(invoiceForm, null, username);
            raised.add(invoice);

            LocalDateTime now = LocalDateTime.now();
            for (TimeEntry entry : entries) {
                entry.setStatus(TimeEntryStatus.INVOICED);
                entry.setInvoiceId(invoice.getId());
                entry.setInvoiceNo(invoice.getInvoiceNo());
                entry.setInvoicedAt(now);
                timeEntryRepository.save(entry);
                billedEntries++;
                billedAmount = billedAmount.add(zero(entry.getBillableAmount()));
            }
            auditService.log(MODULE, "BILL_TIME", "invoice#" + invoice.getId(),
                    entries.size() + " entries → " + invoice.getInvoiceNo());
        }

        return new BillingResult(raised, billedEntries, billedAmount, skipped);
    }

    private static List<InvoiceLineDraft> oneLinePerEntry(List<TimeEntry> entries) {
        List<InvoiceLineDraft> drafts = new ArrayList<>();
        for (TimeEntry entry : entries) {
            drafts.add(new InvoiceLineDraft(describe(entry), zero(entry.getHours()),
                    zero(entry.getBillRate())));
        }
        return drafts;
    }

    /**
     * Rolls a project's hours into one line <em>per rate</em>, not one line per project. Two rates
     * averaged into a single line would make quantity times price disagree with the hours actually
     * worked, and the difference would land on the customer.
     */
    private static List<InvoiceLineDraft> rollUpByProjectAndRate(List<TimeEntry> entries) {
        Map<String, InvoiceLineDraft> grouped = new LinkedHashMap<>();
        for (TimeEntry entry : entries) {
            BigDecimal rate = zero(entry.getBillRate());
            String key = entry.getProjectId() + "@" + rate.toPlainString();
            InvoiceLineDraft existing = grouped.get(key);
            BigDecimal hours = zero(entry.getHours());
            if (existing == null) {
                grouped.put(key, new InvoiceLineDraft(
                        entry.getProjectCode() + " — " + entry.getProjectName(), hours, rate));
            } else {
                grouped.put(key, new InvoiceLineDraft(existing.description(),
                        existing.hours().add(hours), rate));
            }
        }
        return new ArrayList<>(grouped.values());
    }

    private static String describe(TimeEntry entry) {
        StringBuilder text = new StringBuilder();
        text.append(entry.getProjectCode()).append(" · ").append(entry.getWorkDate());
        if (entry.getTask() != null) {
            text.append(" · ").append(entry.getTask());
        }
        if (entry.getDescription() != null) {
            text.append(" — ").append(entry.getDescription());
        }
        text.append(" (").append(entry.getPerson()).append(')');
        return text.toString();
    }

    // ----- Helpers -----

    private TimeEntry log(TimeEntry entry, String action) {
        auditService.log(MODULE, action, "timeEntry#" + entry.getId(),
                entry.getProjectCode() + " " + entry.getPerson() + " " + entry.getHours() + "h");
        return entry;
    }

    private static BigDecimal orDefault(BigDecimal given, BigDecimal fallback) {
        if (given != null && given.signum() != 0) {
            return given.setScale(2, RoundingMode.HALF_UP);
        }
        return zero(fallback);
    }

    public static TimeEntryStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return TimeEntryStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
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

    private record InvoiceLineDraft(String description, BigDecimal hours, BigDecimal rate) {}

    public record BillableView(List<BillableGroup> groups, BigDecimal hours, BigDecimal amount) {

        public boolean isEmpty() {
            return groups.isEmpty();
        }

        public int entryCount() {
            int count = 0;
            for (BillableGroup group : groups) {
                count += group.count();
            }
            return count;
        }
    }

    public record BillableGroup(Long customerId, String customerName, List<TimeEntry> entries) {

        public BigDecimal hours() {
            BigDecimal total = BigDecimal.ZERO;
            for (TimeEntry entry : entries) {
                total = total.add(zero(entry.getHours()));
            }
            return total;
        }

        public BigDecimal amount() {
            BigDecimal total = BigDecimal.ZERO;
            for (TimeEntry entry : entries) {
                total = total.add(zero(entry.getBillableAmount()));
            }
            return total;
        }

        public int count() {
            return entries.size();
        }
    }

    public record BillingResult(List<Invoice> invoices, int entriesBilled,
                                BigDecimal amount, int skipped) {

        public int invoiceCount() {
            return invoices.size();
        }

        public String invoiceNumbers() {
            StringBuilder text = new StringBuilder();
            for (Invoice invoice : invoices) {
                if (!text.isEmpty()) {
                    text.append(", ");
                }
                text.append(invoice.getInvoiceNo());
            }
            return text.toString();
        }
    }

    public record TimeSummary(long draft, long approved, long rejected, long invoiced,
                              BigDecimal draftHours, BigDecimal approvedHours) {}
}
