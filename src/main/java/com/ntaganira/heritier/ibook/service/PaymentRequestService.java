/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : PaymentRequestService.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : Recording that a customer was asked to pay, and what they were told
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.PaymentRequestForm;
import com.ntaganira.heritier.ibook.entity.Company;
import com.ntaganira.heritier.ibook.entity.Invoice;
import com.ntaganira.heritier.ibook.entity.NumberingSequence;
import com.ntaganira.heritier.ibook.entity.PaymentRequest;
import com.ntaganira.heritier.ibook.enums.DocumentStatus;
import com.ntaganira.heritier.ibook.enums.IntegrationProvider;
import com.ntaganira.heritier.ibook.repository.CompanyRepository;
import com.ntaganira.heritier.ibook.repository.InvoiceRepository;
import com.ntaganira.heritier.ibook.repository.NumberingSequenceRepository;
import com.ntaganira.heritier.ibook.repository.PaymentRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Records that a customer was asked to pay an invoice, and what they were told to do.
 *
 * <p><strong>No money moves and no link is created.</strong> A payment link is issued by a gateway,
 * which collects the money and reports back. There is no gateway here, and generating a URL that
 * looked like one would be the single most dangerous thing on this page, because somebody would send
 * it to a customer who would then believe they had paid.
 *
 * <p>So this does the part that needs no gateway, and which the gateway was only ever a convenience
 * for: it composes the payment instructions, records that they went out, and keeps them against the
 * invoice. Whoever is chasing the invoice sends them. In Rwanda a MoMo code or bank details passed on
 * by message is how this is usually done anyway.
 *
 * <p><strong>Nothing here is a payment.</strong> A request does not touch the ledger, does not reduce
 * what the customer owes and does not appear in the invoice's balance. Money is recorded when a
 * receipt is entered against the invoice, through the ordinary receipt path, and not before. Whether a
 * request has been settled is read off the invoice rather than stored, because a second copy of that
 * answer would eventually disagree with the ledger, and the day it did it would be the one somebody
 * believed.
 */
@Service
public class PaymentRequestService {

    private static final Random RANDOM = new Random();

    private final PaymentRequestRepository paymentRequestRepository;
    private final InvoiceRepository invoiceRepository;
    private final CompanyRepository companyRepository;
    private final NumberingSequenceRepository numberingSequenceRepository;
    private final IntegrationService integrationService;

    public PaymentRequestService(PaymentRequestRepository paymentRequestRepository,
                                InvoiceRepository invoiceRepository,
                                CompanyRepository companyRepository,
                                NumberingSequenceRepository numberingSequenceRepository,
                                IntegrationService integrationService) {
        this.paymentRequestRepository = paymentRequestRepository;
        this.invoiceRepository = invoiceRepository;
        this.companyRepository = companyRepository;
        this.numberingSequenceRepository = numberingSequenceRepository;
        this.integrationService = integrationService;
    }

    /**
     * Everything the page shows: the requests made, the invoices still owing, and whether any
     * collection channel has even been configured.
     */
    @Transactional(readOnly = true)
    public Overview overview(LocalDate from, LocalDate to) {
        List<PaymentRequest> requests = paymentRequestRepository
                .findByRequestedOnBetweenOrderByRequestedOnDescIdDesc(from, to);

        // The invoice is read once per distinct invoice, not once per request, and the settlement
        // answer comes from it rather than from anything stored on the request.
        Map<Long, Invoice> invoices = new LinkedHashMap<>();
        List<Row> rows = new ArrayList<>();
        for (PaymentRequest request : requests) {
            Invoice invoice = invoices.computeIfAbsent(request.getInvoiceId(),
                    id -> invoiceRepository.findById(id).orElse(null));
            BigDecimal outstanding = outstandingOf(invoice);
            rows.add(new Row(request, outstanding, invoice == null ? null : invoice.getTotal(),
                    invoice != null && outstanding.signum() <= 0));
        }

        return new Overview(from, to, rows, unpaid(), gatewayConfigured());
    }

    /**
     * Invoices that have been issued and are not settled. These are what a request can be made
     * against; a draft cannot be, because the customer has not been billed yet.
     */
    @Transactional(readOnly = true)
    public List<Owing> unpaid() {
        List<Owing> owing = new ArrayList<>();
        for (Invoice invoice : invoiceRepository.findAll()) {
            if (invoice.getStatus() == DocumentStatus.DRAFT
                    || invoice.getStatus() == DocumentStatus.VOID) {
                continue;
            }
            BigDecimal outstanding = outstandingOf(invoice);
            if (outstanding.signum() <= 0) {
                continue;
            }
            owing.add(new Owing(invoice.getId(), invoice.getInvoiceNo(), invoice.getCustomerId(),
                    invoice.getCustomerName(), invoice.getIssueDate(), invoice.getDueDate(),
                    invoice.getTotal(), outstanding, invoice.getCurrencyCode(),
                    paymentRequestRepository.countByInvoiceIdAndCancelledFalse(invoice.getId())));
        }
        owing.sort((a, b) -> {
            if (a.dueDate() == null || b.dueDate() == null) {
                return 0;
            }
            return a.dueDate().compareTo(b.dueDate());
        });
        return owing;
    }

    /**
     * Whether any collection channel has been configured at all.
     *
     * <p>Reported so the page can be clear about what it means: even with all three configured,
     * nothing is collected, because settings are not a connection. It is here to show that no channel
     * has been set up either, so the instructions below are all there is.
     */
    @Transactional(readOnly = true)
    public boolean gatewayConfigured() {
        for (IntegrationProvider provider : List.of(IntegrationProvider.PAYMENT_GATEWAY,
                IntegrationProvider.MTN_MOMO, IntegrationProvider.AIRTEL_MONEY)) {
            IntegrationService.Entry entry = integrationService.registry(provider).single();
            if (entry != null && entry.isReady()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Suggests where the money should be sent, from whatever has been recorded for that channel.
     *
     * <p>Only the account identifier is used — a merchant code or account number, which appears on
     * receipts anyway. Nothing secret is read, and where nothing has been recorded the field is left
     * empty for somebody to fill in by hand rather than filled with a plausible-looking guess.
     */
    @Transactional(readOnly = true)
    public String suggestedPayTo(String channel) {
        IntegrationProvider provider = switch (channel == null ? "" : channel) {
            case "MTN_MOMO" -> IntegrationProvider.MTN_MOMO;
            case "AIRTEL_MONEY" -> IntegrationProvider.AIRTEL_MONEY;
            case "CARD" -> IntegrationProvider.PAYMENT_GATEWAY;
            default -> null;
        };
        if (provider == null) {
            return "";
        }
        IntegrationService.Entry entry = integrationService.registry(provider).single();
        if (entry == null || entry.row().getParticipantId() == null) {
            return "";
        }
        return entry.row().getParticipantId();
    }

    @Transactional
    public PaymentRequest create(PaymentRequestForm form, String user) {
        Invoice invoice = invoiceRepository.findById(form.invoiceId())
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found: " + form.invoiceId()));

        if (invoice.getStatus() == DocumentStatus.DRAFT) {
            throw new IllegalStateException("That invoice is still a draft, so the customer has not "
                    + "been billed for it. Asking them to pay a figure that may still change is how a "
                    + "customer ends up paying the wrong amount.");
        }
        if (invoice.getStatus() == DocumentStatus.VOID) {
            throw new IllegalStateException("That invoice has been voided. Nothing is owed on it.");
        }

        BigDecimal outstanding = outstandingOf(invoice);
        if (outstanding.signum() <= 0) {
            throw new IllegalStateException("Nothing is outstanding on " + invoice.getInvoiceNo()
                    + ". Asking for payment of a settled invoice is how a customer comes to pay twice.");
        }

        BigDecimal amount = form.amount() == null || form.amount().signum() <= 0
                ? outstanding
                : form.amount();
        if (amount.compareTo(outstanding) > 0) {
            throw new IllegalStateException("That is more than the " + outstanding.toPlainString()
                    + " still outstanding on " + invoice.getInvoiceNo() + ". A request for more than is "
                    + "owed would be asking for an overpayment, which then has to be refunded or "
                    + "carried, and neither is what anybody intended.");
        }

        String reference = isBlank(form.reference()) ? invoice.getInvoiceNo() : form.reference().trim();
        PaymentRequest request = PaymentRequest.builder()
                .requestNo(nextRequestNo())
                .invoiceId(invoice.getId())
                .invoiceNo(invoice.getInvoiceNo())
                .customerId(invoice.getCustomerId())
                .customerName(invoice.getCustomerName())
                .amount(amount)
                .currencyCode(invoice.getCurrencyCode())
                .channel(form.channel())
                .payTo(trimToNull(form.payTo()))
                .reference(reference)
                .requestedOn(form.requestedOn() == null ? LocalDate.now() : form.requestedOn())
                .notes(trimToNull(form.notes()))
                .createdBy(user)
                .build();
        // Composed once and stored as sent. Rebuilding it later from settings that have since changed
        // would show a message the customer never received.
        request.setInstructions(compose(request));
        return paymentRequestRepository.save(request);
    }

    /**
     * Cancels a request. It is never deleted: the fact that a customer was asked to pay is part of the
     * history of chasing the invoice, and a request withdrawn is a different thing from one that was
     * never made.
     */
    @Transactional
    public void cancel(Long id) {
        PaymentRequest request = paymentRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + id));
        request.setCancelled(true);
        paymentRequestRepository.save(request);
    }

    /**
     * The message to send the customer. Plain words, because somebody pastes this into a message and
     * anything cleverer would arrive as markup.
     */
    private String compose(PaymentRequest request) {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElse(null);
        String business = company == null || isBlank(company.getName()) ? "" : company.getName();

        StringBuilder text = new StringBuilder();
        if (!business.isBlank()) {
            text.append(business).append(": ");
        }
        text.append("invoice ").append(request.getInvoiceNo()).append(", ")
                .append(request.getCurrencyCode()).append(" ")
                .append(request.getAmount().toPlainString()).append(" due.");
        if (request.getPayTo() != null) {
            text.append(" Pay to ").append(request.getPayTo()).append('.');
        }
        text.append(" Quote reference ").append(request.getReference()).append('.');
        return text.toString();
    }

    private static BigDecimal outstandingOf(Invoice invoice) {
        if (invoice == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal paid = invoice.getAmountPaid() == null ? BigDecimal.ZERO : invoice.getAmountPaid();
        BigDecimal credited = invoice.getCreditedAmount() == null
                ? BigDecimal.ZERO : invoice.getCreditedAmount();
        return invoice.getTotal().subtract(paid).subtract(credited);
    }

    private String nextRequestNo() {
        NumberingSequence seq = numberingSequenceRepository.findByDocType("PAYREQUEST").orElse(null);
        if (seq != null) {
            long next = seq.getNextNumber();
            String prefix = seq.getPrefix() == null ? "PRQ-" : seq.getPrefix();
            String suffix = seq.getSuffix() == null ? "" : seq.getSuffix();
            int padding = seq.getPadding() == 0 ? 4 : seq.getPadding();
            String number = prefix + String.format("%0" + padding + "d", next) + suffix;
            seq.setNextNumber(next + 1);
            numberingSequenceRepository.save(seq);
            return number;
        }
        return "PRQ-" + LocalDate.now().getYear() + "-" + String.format("%05d", RANDOM.nextInt(100000));
    }

    /** One request made, with the invoice's current position read off the invoice. */
    public record Row(PaymentRequest request, BigDecimal outstanding, BigDecimal invoiceTotal,
                      boolean settled) {

        /** Still waiting: asked for, not withdrawn, and the invoice is not yet settled. */
        public boolean isAwaiting() {
            return !request.isCancelled() && !settled;
        }
    }

    /** An issued invoice with something still owing on it. */
    public record Owing(Long invoiceId, String invoiceNo, Long customerId, String customerName,
                        LocalDate issueDate, LocalDate dueDate, BigDecimal total,
                        BigDecimal outstanding, String currencyCode, long requestsMade) {

        public boolean isAsked() {
            return requestsMade > 0;
        }

        public boolean isOverdue() {
            return dueDate != null && dueDate.isBefore(LocalDate.now());
        }
    }

    public record Overview(LocalDate from, LocalDate to, List<Row> rows, List<Owing> owing,
                           boolean gatewayConfigured) {

        public boolean hasAny() {
            return !rows.isEmpty();
        }

        public boolean hasOwing() {
            return !owing.isEmpty();
        }

        public long awaiting() {
            return rows.stream().filter(Row::isAwaiting).count();
        }

        /**
         * Summed without conversion, like every other total in this application. Right for a
         * single-currency ledger and wrong the moment an invoice is raised in anything else. Stated
         * rather than hidden, and the page labels the figure so nobody reads it as a converted total.
         */
        public BigDecimal owingTotal() {
            return owing.stream().map(Owing::outstanding).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        /** Whether more than one currency is in the figure above, which is when it stops being true. */
        public boolean isMixedCurrency() {
            return owing.stream().map(Owing::currencyCode).distinct().count() > 1;
        }

        /** How many owing invoices nobody has asked about yet, which is the useful thing to act on. */
        public long neverAsked() {
            return owing.stream().filter(o -> !o.isAsked()).count();
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
