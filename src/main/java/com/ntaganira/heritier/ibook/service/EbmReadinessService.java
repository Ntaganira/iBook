/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : EbmReadinessService.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : Which invoices could be fiscalised, and what each one still lacks
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.entity.Company;
import com.ntaganira.heritier.ibook.entity.Customer;
import com.ntaganira.heritier.ibook.entity.Invoice;
import com.ntaganira.heritier.ibook.entity.InvoiceLine;
import com.ntaganira.heritier.ibook.entity.Product;
import com.ntaganira.heritier.ibook.enums.DocumentStatus;
import com.ntaganira.heritier.ibook.enums.IntegrationProvider;
import com.ntaganira.heritier.ibook.repository.CompanyRepository;
import com.ntaganira.heritier.ibook.repository.CustomerRepository;
import com.ntaganira.heritier.ibook.repository.InvoiceRepository;
import com.ntaganira.heritier.ibook.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reports which posted invoices could be fiscalised through EBM, and what each one still lacks.
 *
 * <p><strong>Nothing is transmitted and no invoice is fiscalised.</strong> That is not a limitation to
 * be worked around: an invoice is fiscalised when the RRA's signing device has signed it and returned
 * a receipt, and a page that marked an invoice as fiscalised without that would be worse than useless.
 * It would be a false statutory record, shown to whoever later has to answer for the return.
 *
 * <p>What can honestly be done is the preparation, and it is the part that actually takes time. EBM
 * rejects an invoice for missing data, one invoice at a time, after certification — by which point the
 * invoices are already issued and the gaps have to be chased retrospectively through customers who
 * have moved on. Listing the gaps now, against invoices that already exist, is work that is not wasted
 * whichever way the integration is eventually built.
 *
 * <p>One gap cannot be reported per invoice because it is missing from the whole application: EBM
 * requires an RRA <em>item classification code</em> on every line, and there is no field for one on a
 * product. A product's SKU is the business's own code and is not the same thing. That is recorded here
 * as a structural gap rather than counted against each invoice, because no amount of editing invoices
 * would fix it.
 */
@Service
public class EbmReadinessService {

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final CompanyRepository companyRepository;
    private final IntegrationService integrationService;

    public EbmReadinessService(InvoiceRepository invoiceRepository,
                              CustomerRepository customerRepository,
                              ProductRepository productRepository,
                              CompanyRepository companyRepository,
                              IntegrationService integrationService) {
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.companyRepository = companyRepository;
        this.integrationService = integrationService;
    }

    /**
     * Reviews every invoice that has been issued in the period.
     *
     * <p>Drafts are left out. A draft is not an invoice yet and may still change, so holding it to a
     * statutory requirement would report gaps nobody needs to act on. Voided invoices are kept,
     * because a voided invoice that was already issued still has to be accounted for to the RRA.
     */
    @Transactional(readOnly = true)
    public Review review(LocalDate from, LocalDate to) {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElse(null);
        String sellerTin = company == null ? null : company.getTin();

        List<Invoice> invoices = invoiceRepository.findAll().stream()
                .filter(i -> i.getStatus() != DocumentStatus.DRAFT)
                .filter(i -> i.getIssueDate() != null
                        && !i.getIssueDate().isBefore(from) && !i.getIssueDate().isAfter(to))
                .sorted((a, b) -> b.getIssueDate().compareTo(a.getIssueDate()))
                .toList();

        // Customers and products are read once each rather than per line. A hundred invoices of ten
        // lines is a thousand lookups otherwise, on a page whose whole job is to be read at a glance.
        Map<Long, Customer> customers = new HashMap<>();
        Map<Long, Product> products = new HashMap<>();

        List<Row> rows = new ArrayList<>();
        int ready = 0;
        for (Invoice invoice : invoices) {
            List<String> gaps = new ArrayList<>();

            if (isBlank(sellerTin)) {
                gaps.add("sellerTin");
            }

            Customer customer = invoice.getCustomerId() == null ? null
                    : customers.computeIfAbsent(invoice.getCustomerId(),
                            id -> customerRepository.findById(id).orElse(null));
            if (customer == null || isBlank(customer.getTaxId())) {
                gaps.add("buyerTin");
            }

            boolean missingUnit = false;
            boolean missingItemLink = false;
            for (InvoiceLine line : invoice.getLines()) {
                if (line.getProductId() == null) {
                    // A free-text line has no product behind it, so it has no unit and no code to
                    // classify. EBM will not take it as it stands.
                    missingItemLink = true;
                    continue;
                }
                Product product = products.computeIfAbsent(line.getProductId(),
                        id -> productRepository.findById(id).orElse(null));
                if (product == null || isBlank(product.getUnit())) {
                    missingUnit = true;
                }
            }
            if (missingItemLink) {
                gaps.add("lineProduct");
            }
            if (missingUnit) {
                gaps.add("lineUnit");
            }

            if (invoice.getLines().isEmpty()) {
                gaps.add("noLines");
            }

            // Every invoice lacks this, always, because nothing sets it. Reported per invoice all the
            // same: it is the difference between an invoice that is ready to send and one that has
            // been fiscalised, and collapsing the two is exactly the mistake worth preventing.
            boolean fiscalised = !isBlank(invoice.getEbmReference());

            if (gaps.isEmpty()) {
                ready++;
            }
            rows.add(new Row(invoice.getId(), invoice.getInvoiceNo(), invoice.getIssueDate(),
                    invoice.getCustomerName(), invoice.getTotal(), invoice.getTaxAmount(),
                    invoice.getStatus().name(), gaps, fiscalised, invoice.getEbmReference()));
        }

        IntegrationService.Registry registry = integrationService.registry(IntegrationProvider.EBM);
        return new Review(from, to, rows, ready, sellerTin,
                registry.single() != null && registry.single().isReady());
    }

    /** One issued invoice and what EBM would reject it for. */
    public record Row(Long id, String invoiceNo, LocalDate issueDate, String customerName,
                      java.math.BigDecimal total, java.math.BigDecimal taxAmount, String status,
                      List<String> gaps, boolean fiscalised, String ebmReference) {

        /**
         * Ready to be <em>prepared</em> — every field EBM needs is present. It is not fiscalised, and
         * the page is careful to word it that way.
         */
        public boolean isDataComplete() {
            return gaps.isEmpty();
        }
    }

    public record Review(LocalDate from, LocalDate to, List<Row> rows, int dataComplete,
                         String sellerTin, boolean settingsRecorded) {

        public boolean hasAny() {
            return !rows.isEmpty();
        }

        public int total() {
            return rows.size();
        }

        public int incomplete() {
            return rows.size() - dataComplete;
        }

        /**
         * How many carry a fiscal receipt. Always zero, and stated rather than hidden: the field
         * exists on an invoice and nothing in this application ever writes to it.
         */
        public long fiscalised() {
            return rows.stream().filter(Row::fiscalised).count();
        }

        public boolean hasSellerTin() {
            return sellerTin != null && !sellerTin.isBlank();
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
