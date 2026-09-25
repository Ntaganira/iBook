/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : ProgressBillingService.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Claims a share of an agreed fixed price and turns it into a draft invoice
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.ClaimInvoiceForm;
import com.ntaganira.heritier.ibook.dto.InvoiceForm;
import com.ntaganira.heritier.ibook.dto.ProgressBillingForm;
import com.ntaganira.heritier.ibook.entity.Account;
import com.ntaganira.heritier.ibook.entity.Invoice;
import com.ntaganira.heritier.ibook.entity.ProgressBilling;
import com.ntaganira.heritier.ibook.entity.Project;
import com.ntaganira.heritier.ibook.enums.AccountType;
import com.ntaganira.heritier.ibook.enums.ProgressBillingMethod;
import com.ntaganira.heritier.ibook.enums.ProgressBillingStatus;
import com.ntaganira.heritier.ibook.repository.AccountRepository;
import com.ntaganira.heritier.ibook.repository.ProgressBillingRepository;
import com.ntaganira.heritier.ibook.repository.ProjectRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Progress billing is how a <strong>fixed-price</strong> job gets paid before it is finished: the
 * customer is asked for a share of the agreed price rather than for the hours worked. It is the
 * counterpart of {@code /projects/billable}, which exists only for jobs charged by the hour, and
 * the two can never both apply to one job.
 *
 * <p>A claim is <strong>cumulative</strong>, which is how progress claims are actually written and
 * is not a detail. "40% complete" means forty per cent of the contract has been earned in total, so
 * this claim is forty per cent less everything already claimed. Storing each claim as a standalone
 * slice instead would let rounding on each one drift the total away from the contract price.
 *
 * <p><strong>Retention</strong> is money the customer holds back until the job is signed off. Here
 * it simply reduces what this claim invoices, and the amount held is carried on the claim and
 * totalled per job. Nothing posts a retention receivable: that would need the invoice to carry a
 * retention line of its own, and inventing one on the invoice would misstate what is owed. Getting
 * retention back is a later claim on the flat-amount method with no retention on it.
 *
 * <p>Like every other conversion in the app, raising a claim writes a <em>draft</em> invoice
 * through {@link InvoiceService} and nothing reaches the ledger until that invoice is posted.
 */
@Service
public class ProgressBillingService {

    private static final String MODULE = "projects";
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final ProgressBillingRepository progressBillingRepository;
    private final ProjectRepository projectRepository;
    private final AccountRepository accountRepository;
    private final InvoiceService invoiceService;
    private final AuditService auditService;

    public ProgressBillingService(ProgressBillingRepository progressBillingRepository,
                                  ProjectRepository projectRepository,
                                  AccountRepository accountRepository,
                                  InvoiceService invoiceService,
                                  AuditService auditService) {
        this.progressBillingRepository = progressBillingRepository;
        this.projectRepository = projectRepository;
        this.accountRepository = accountRepository;
        this.invoiceService = invoiceService;
        this.auditService = auditService;
    }

    // ----- Queries -----

    @Transactional(readOnly = true)
    public Page<ProgressBilling> list(String q, String status, Long projectId, Pageable pageable) {
        return progressBillingRepository.search(trimToNull(q), parseStatus(status), projectId,
                pageable);
    }

    @Transactional(readOnly = true)
    public ProgressBilling get(Long id) {
        return id == null ? null : progressBillingRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<ProgressBilling> forProject(Long projectId) {
        return progressBillingRepository.findByProjectIdOrderByClaimNumberAsc(projectId);
    }

    @Transactional(readOnly = true)
    public ClaimSummary summary() {
        return new ClaimSummary(progressBillingRepository.count(),
                progressBillingRepository.countByStatus(ProgressBillingStatus.DRAFT),
                progressBillingRepository.countByStatus(ProgressBillingStatus.INVOICED),
                progressBillingRepository.countByStatus(ProgressBillingStatus.CANCELLED));
    }

    /**
     * The jobs a claim can be raised against: a fixed price, somebody to bill, a price above zero
     * and not cancelled. Everything else is either billed by the hour or billed to nobody.
     */
    @Transactional(readOnly = true)
    public List<Project> claimableProjects(Long currentId) {
        List<Project> rows = new ArrayList<>();
        for (Project project : projectRepository.findAllByOrderByCodeAsc()) {
            if ((currentId != null && currentId.equals(project.getId())) || isClaimable(project)) {
                rows.add(project);
            }
        }
        return rows;
    }

    private static boolean isClaimable(Project project) {
        return project.isFixedPrice()
                && !project.isCancelled()
                && project.getCustomerId() != null
                && project.getFixedPrice() != null
                && project.getFixedPrice().signum() > 0;
    }

    @Transactional(readOnly = true)
    public List<Account> revenueAccounts() {
        List<Account> rows = new ArrayList<>();
        for (Account account : accountRepository.findByActiveTrueOrderByCodeAsc()) {
            if (account.getType() == AccountType.REVENUE) {
                rows.add(account);
            }
        }
        return rows;
    }

    /** Where a job stands against its contract: claimed, held back, and still to come. */
    @Transactional(readOnly = true)
    public Position positionOf(Project project) {
        if (project == null) {
            return null;
        }
        BigDecimal contract = zero(project.getFixedPrice());
        BigDecimal billed = zero(progressBillingRepository.billedOn(project.getId()));
        BigDecimal retained = zero(progressBillingRepository.retainedOn(project.getId()));
        BigDecimal remaining = contract.subtract(billed);
        BigDecimal percent = contract.signum() == 0 ? BigDecimal.ZERO
                : billed.multiply(HUNDRED).divide(contract, 1, RoundingMode.HALF_UP);
        long openDrafts = progressBillingRepository.countByProjectIdAndStatus(
                project.getId(), ProgressBillingStatus.DRAFT);
        return new Position(project, contract, billed, retained, remaining, percent, openDrafts);
    }

    // ----- Create / update -----

    @Transactional
    public ProgressBilling save(ProgressBillingForm form, Long id, String username) {
        Project project = form.projectId() == null ? null
                : projectRepository.findById(form.projectId()).orElse(null);
        if (project == null) {
            throw new IllegalArgumentException("Choose the job this claim is against");
        }
        if (!project.isFixedPrice()) {
            throw new IllegalArgumentException("Only a fixed-price job is billed by progress. "
                    + (project.isHourlyBillable()
                            ? "A job charged by the hour bills its approved time instead."
                            : "This job is not billed to anybody at all."));
        }
        if (project.isCancelled()) {
            throw new IllegalStateException("A cancelled job cannot be claimed against");
        }
        if (project.getCustomerId() == null) {
            throw new IllegalArgumentException("That job has no customer to bill");
        }
        BigDecimal contract = zero(project.getFixedPrice());
        if (contract.signum() <= 0) {
            throw new IllegalArgumentException(
                    "That job has no agreed price on it. Set one before claiming against it.");
        }

        ProgressBilling claim;
        if (id == null) {
            if (progressBillingRepository.countByProjectIdAndStatus(
                    project.getId(), ProgressBillingStatus.DRAFT) > 0) {
                throw new IllegalArgumentException("That job already has a claim in draft. "
                        + "Raise or cancel it before starting another.");
            }
            claim = new ProgressBilling();
            claim.setCreatedBy(username);
            claim.setStatus(ProgressBillingStatus.DRAFT);
            claim.setClaimNumber(progressBillingRepository.lastClaimNumber(project.getId()) + 1);
        } else {
            claim = progressBillingRepository.findById(id).orElseThrow();
            if (!claim.isEditable()) {
                throw new IllegalStateException(
                        "This claim has already been invoiced and cannot be changed");
            }
        }

        BigDecimal previously = zero(progressBillingRepository.billedOn(project.getId()));
        ProgressBillingMethod method = parseMethod(form.method());
        BigDecimal cumulative = cumulativeFor(method, form, contract, previously);

        if (cumulative.compareTo(previously) <= 0) {
            throw new IllegalArgumentException("This claim comes to nothing. "
                    + formatMoney(previously) + " has already been claimed on this job.");
        }
        if (cumulative.compareTo(contract) > 0) {
            throw new IllegalArgumentException("That would claim " + formatMoney(cumulative)
                    + " against an agreed price of " + formatMoney(contract)
                    + ". Raise the agreed price on the job first if the contract has changed.");
        }

        BigDecimal retentionPercent = form.retentionPercentValue();
        if (retentionPercent.signum() < 0 || retentionPercent.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException("Retention has to be between 0 and 100 per cent");
        }

        BigDecimal gross = cumulative.subtract(previously).setScale(2, RoundingMode.HALF_UP);
        BigDecimal retention = gross.multiply(retentionPercent)
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal net = gross.subtract(retention);

        claim.setProjectId(project.getId());
        claim.setProjectCode(project.getCode());
        claim.setProjectName(project.getName());
        claim.setCustomerId(project.getCustomerId());
        claim.setCustomerName(project.getCustomerName());
        claim.setClaimDate(form.claimDate() == null ? LocalDate.now() : form.claimDate());
        claim.setMethod(method);
        claim.setContractValue(contract);
        claim.setPercentComplete(percentOf(cumulative, contract));
        claim.setPreviouslyBilled(previously);
        claim.setCumulativeBilled(cumulative);
        claim.setGrossAmount(gross);
        claim.setRetentionPercent(retentionPercent);
        claim.setRetentionAmount(retention);
        claim.setNetAmount(net);
        claim.setDescription(trimToNull(form.description()));
        claim.setNotes(trimToNull(form.notes()));

        ProgressBilling saved = progressBillingRepository.save(claim);
        auditService.log(MODULE, id == null ? "CREATE_CLAIM" : "UPDATE_CLAIM",
                "progressBilling#" + saved.getId(),
                saved.getClaimLabel() + " " + formatMoney(saved.getNetAmount()));
        return saved;
    }

    /**
     * The percentage method states where the job has got to overall; the flat-amount method states
     * what this claim asks for on top of what has gone before. Both end up as one cumulative
     * figure so that the two can be mixed on the same job without the total drifting.
     */
    private static BigDecimal cumulativeFor(ProgressBillingMethod method, ProgressBillingForm form,
                                            BigDecimal contract, BigDecimal previously) {
        if (method.isPercent()) {
            BigDecimal percent = form.percentCompleteValue();
            if (percent.signum() <= 0 || percent.compareTo(HUNDRED) > 0) {
                throw new IllegalArgumentException(
                        "Percentage complete has to be above 0 and no more than 100");
            }
            return contract.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        }
        BigDecimal amount = form.amountValue();
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("A claim has to be for more than nothing");
        }
        return previously.add(amount).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percentOf(BigDecimal cumulative, BigDecimal contract) {
        return contract.signum() == 0 ? BigDecimal.ZERO
                : cumulative.multiply(HUNDRED).divide(contract, 2, RoundingMode.HALF_UP);
    }

    // ----- Turning a claim into money -----

    /**
     * Raises the draft invoice for a claim.
     *
     * <p>The claim is re-read and re-checked inside the transaction rather than trusted from the
     * page it was raised on: what has already been claimed can have moved since the draft was
     * written, and a stale claim would otherwise ask the customer for money twice.
     */
    @Transactional
    public ProgressBilling raise(Long id, ClaimInvoiceForm form, String username) {
        ProgressBilling claim = progressBillingRepository.findById(id).orElseThrow();
        if (!claim.isDraft()) {
            throw new IllegalStateException("Only a claim still in draft can be invoiced");
        }
        Project project = projectRepository.findById(claim.getProjectId()).orElse(null);
        if (project == null || !project.isFixedPrice() || project.isCancelled()) {
            throw new IllegalStateException(
                    "That job no longer bills by progress. Reload the page and check it.");
        }

        BigDecimal previously = zero(progressBillingRepository.billedOn(claim.getProjectId()));
        if (previously.compareTo(claim.getPreviouslyBilled()) != 0) {
            throw new IllegalStateException("Another claim has been raised on this job since this "
                    + "one was written — " + formatMoney(previously) + " is now claimed, not "
                    + formatMoney(claim.getPreviouslyBilled())
                    + ". Edit this claim so the figures line up.");
        }
        if (claim.getNetAmount().signum() <= 0) {
            throw new IllegalStateException("This claim invoices nothing once retention is taken "
                    + "off. Lower the retention or claim more.");
        }

        LocalDate issue = form.issueDate() == null ? LocalDate.now() : form.issueDate();
        LocalDate due = form.dueDate() == null ? issue.plusDays(30) : form.dueDate();
        if (due.isBefore(issue)) {
            throw new IllegalArgumentException("The due date falls before the issue date");
        }

        InvoiceForm invoiceForm = new InvoiceForm();
        invoiceForm.setCustomerId(claim.getCustomerId());
        invoiceForm.setIssueDate(issue);
        invoiceForm.setDueDate(due);
        invoiceForm.setReference(trimToNull(form.reference()) == null
                ? claim.getProjectCode() : form.reference().trim());
        invoiceForm.setCustomerMessage(trimToNull(form.customerMessage()));
        invoiceForm.setPostNow(false);

        InvoiceForm.Line line = invoiceForm.getLines().get(0);
        line.setDescription(describe(claim));
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(claim.getNetAmount());
        line.setTaxRateId(form.taxRateId());
        line.setRevenueAccountId(form.revenueAccountId());

        Invoice invoice = invoiceService.saveInvoice(invoiceForm, null, username);

        claim.setStatus(ProgressBillingStatus.INVOICED);
        claim.setInvoiceId(invoice.getId());
        claim.setInvoiceNo(invoice.getInvoiceNo());
        claim.setInvoicedAt(LocalDateTime.now());
        ProgressBilling saved = progressBillingRepository.save(claim);

        auditService.log(MODULE, "RAISE_CLAIM", "progressBilling#" + saved.getId(),
                saved.getClaimLabel() + " → " + invoice.getInvoiceNo());
        return saved;
    }

    private static String describe(ProgressBilling claim) {
        StringBuilder text = new StringBuilder();
        text.append(claim.getProjectCode()).append(" — ").append(claim.getProjectName());
        text.append(" · claim ").append(claim.getClaimNumber());
        if (claim.getMethod().isPercent()) {
            text.append(" · ").append(claim.getPercentComplete().stripTrailingZeros().toPlainString())
                    .append("% complete");
        }
        if (claim.isHoldingRetention()) {
            text.append(" · ").append(claim.getRetentionPercent().stripTrailingZeros().toPlainString())
                    .append("% retention held back");
        }
        if (claim.getDescription() != null) {
            text.append(" — ").append(claim.getDescription());
        }
        return text.toString();
    }

    // ----- Lifecycle -----

    @Transactional
    public ProgressBilling cancel(Long id, String reason) {
        ProgressBilling claim = progressBillingRepository.findById(id).orElseThrow();
        if (claim.isInvoiced()) {
            throw new IllegalStateException("This claim has been invoiced. Void or credit the "
                    + "invoice instead — cancelling the claim would leave the invoice standing.");
        }
        if (claim.isCancelled()) {
            throw new IllegalStateException("This claim is already cancelled");
        }
        claim.setStatus(ProgressBillingStatus.CANCELLED);
        claim.setCancelledReason(trimToNull(reason));
        progressBillingRepository.save(claim);
        auditService.log(MODULE, "CANCEL_CLAIM", "progressBilling#" + id, claim.getClaimLabel());
        return claim;
    }

    @Transactional
    public void delete(Long id) {
        ProgressBilling claim = progressBillingRepository.findById(id).orElse(null);
        if (claim == null) {
            return;
        }
        if (claim.isInvoiced()) {
            throw new IllegalStateException(
                    "An invoiced claim is evidence for a document the customer has. Cancel or "
                            + "credit the invoice instead of deleting the claim.");
        }
        progressBillingRepository.delete(claim);
        auditService.log(MODULE, "DELETE_CLAIM", "progressBilling#" + id, claim.getClaimLabel());
    }

    // ----- Helpers -----

    public static ProgressBillingStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ProgressBillingStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static ProgressBillingMethod parseMethod(String method) {
        if (method == null || method.isBlank()) {
            return ProgressBillingMethod.PERCENT_COMPLETE;
        }
        try {
            return ProgressBillingMethod.valueOf(method.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ProgressBillingMethod.PERCENT_COMPLETE;
        }
    }

    private static String formatMoney(BigDecimal value) {
        return zero(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
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

    public record Position(Project project, BigDecimal contractValue, BigDecimal billed,
                           BigDecimal retained, BigDecimal remaining, BigDecimal percentBilled,
                           long openDrafts) {

        public boolean isFullyBilled() {
            return contractValue.signum() > 0 && remaining.signum() <= 0;
        }

        public boolean hasRetention() {
            return retained.signum() > 0;
        }
    }

    public record ClaimSummary(long all, long draft, long invoiced, long cancelled) {}
}
