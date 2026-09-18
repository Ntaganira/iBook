/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : TaxService.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : VAT return, tax liability and tax transaction reporting
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.entity.Account;
import com.ntaganira.heritier.ibook.entity.Bill;
import com.ntaganira.heritier.ibook.entity.Invoice;
import com.ntaganira.heritier.ibook.entity.JournalLine;
import com.ntaganira.heritier.ibook.enums.AccountType;
import com.ntaganira.heritier.ibook.enums.TaxTreatment;
import com.ntaganira.heritier.ibook.repository.AccountRepository;
import com.ntaganira.heritier.ibook.repository.BillRepository;
import com.ntaganira.heritier.ibook.repository.InvoiceRepository;
import com.ntaganira.heritier.ibook.repository.JournalLineRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TaxService {

    /** Standard Rwanda VAT rate. */
    public static final BigDecimal STANDARD_VAT_RATE = new BigDecimal("18.00");

    private static final String VAT_OUTPUT_CODE = "2101";
    private static final String VAT_INPUT_CODE = "1402";
    private static final List<String> TAX_ACCOUNT_CODES = List.of("2101", "1402", "2102");

    private final InvoiceRepository invoiceRepository;
    private final BillRepository billRepository;
    private final AccountRepository accountRepository;
    private final JournalLineRepository journalLineRepository;

    public TaxService(InvoiceRepository invoiceRepository,
                      BillRepository billRepository,
                      AccountRepository accountRepository,
                      JournalLineRepository journalLineRepository) {
        this.invoiceRepository = invoiceRepository;
        this.billRepository = billRepository;
        this.accountRepository = accountRepository;
        this.journalLineRepository = journalLineRepository;
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** Net of discount — the amount VAT was actually charged on. */
    private static BigDecimal taxableBase(BigDecimal subtotal, BigDecimal discount) {
        return zero(subtotal).subtract(zero(discount));
    }

    // ----- VAT return -----

    @Transactional(readOnly = true)
    public VatReturn vatReturn(LocalDate from, LocalDate to) {
        List<VatDocument> sales = new ArrayList<>();
        BigDecimal taxableSales = BigDecimal.ZERO;
        BigDecimal outputVat = BigDecimal.ZERO;
        BigDecimal zeroRatedSales = BigDecimal.ZERO;

        BigDecimal exemptSales = BigDecimal.ZERO;
        for (Invoice invoice : invoiceRepository.findPostedBetween(from, to)) {
            BigDecimal base = taxableBase(invoice.getSubtotal(), invoice.getDiscountAmount());
            BigDecimal vat = zero(invoice.getTaxAmount());
            BigDecimal[] split = splitByTreatment(invoice.getLines().stream()
                    .map(l -> new Leg(zero(l.getLineSubtotal()), l.getTaxTreatment()))
                    .toList(), base);
            taxableSales = taxableSales.add(split[0]);
            zeroRatedSales = zeroRatedSales.add(split[1]);
            exemptSales = exemptSales.add(split[2]);
            outputVat = outputVat.add(vat);
            sales.add(new VatDocument(invoice.getId(), invoice.getInvoiceNo(), invoice.getIssueDate(),
                    invoice.getCustomerName(), base, vat, zero(invoice.getTotal())));
        }

        List<VatDocument> purchases = new ArrayList<>();
        BigDecimal taxablePurchases = BigDecimal.ZERO;
        BigDecimal inputVat = BigDecimal.ZERO;
        BigDecimal zeroRatedPurchases = BigDecimal.ZERO;

        BigDecimal exemptPurchases = BigDecimal.ZERO;
        for (Bill bill : billRepository.findPostedBetween(from, to)) {
            BigDecimal base = taxableBase(bill.getSubtotal(), bill.getDiscountAmount());
            BigDecimal vat = zero(bill.getTaxAmount());
            BigDecimal[] split = splitByTreatment(bill.getLines().stream()
                    .map(l -> new Leg(zero(l.getLineSubtotal()), l.getTaxTreatment()))
                    .toList(), base);
            taxablePurchases = taxablePurchases.add(split[0]);
            zeroRatedPurchases = zeroRatedPurchases.add(split[1]);
            exemptPurchases = exemptPurchases.add(split[2]);
            inputVat = inputVat.add(vat);
            purchases.add(new VatDocument(bill.getId(), bill.getBillNo(), bill.getBillDate(),
                    bill.getVendorName(), base, vat, zero(bill.getTotal())));
        }

        BigDecimal net = outputVat.subtract(inputVat);

        // Reconcile the document totals against what actually hit the ledger.
        BigDecimal ledgerOutput = periodMovement(VAT_OUTPUT_CODE, from, to, false);
        BigDecimal ledgerInput = periodMovement(VAT_INPUT_CODE, from, to, true);

        return new VatReturn(from, to, sales, purchases,
                taxableSales, zeroRatedSales, exemptSales, outputVat,
                taxablePurchases, zeroRatedPurchases, exemptPurchases, inputVat,
                net, ledgerOutput, ledgerInput,
                outputVat.subtract(ledgerOutput), inputVat.subtract(ledgerInput));
    }

    /**
     * Movement on one account over a period.
     * {@code debitSide} selects debits-minus-credits (input VAT) rather than the reverse.
     */
    private BigDecimal periodMovement(String accountCode, LocalDate from, LocalDate to, boolean debitSide) {
        Account account = accountRepository.findByCodeIgnoreCase(accountCode).orElse(null);
        if (account == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (Object[] row : journalLineRepository.postedTotalsBetween(from, to)) {
            if (!account.getId().equals(((Number) row[0]).longValue())) {
                continue;
            }
            debit = row[1] == null ? BigDecimal.ZERO : (BigDecimal) row[1];
            credit = row[2] == null ? BigDecimal.ZERO : (BigDecimal) row[2];
        }
        return debitSide ? debit.subtract(credit) : credit.subtract(debit);
    }

    private record Leg(BigDecimal amount, TaxTreatment treatment) {}

    /**
     * Splits a document's taxable base into standard, zero-rated and exempt buckets using
     * each line's treatment. The document base is apportioned by line share so the three
     * buckets always add back to it, discount included.
     */
    private static BigDecimal[] splitByTreatment(List<Leg> legs, BigDecimal documentBase) {
        BigDecimal standard = BigDecimal.ZERO;
        BigDecimal zeroRated = BigDecimal.ZERO;
        BigDecimal exempt = BigDecimal.ZERO;
        BigDecimal lineTotal = BigDecimal.ZERO;
        for (Leg leg : legs) {
            lineTotal = lineTotal.add(leg.amount());
        }
        if (lineTotal.signum() == 0) {
            return new BigDecimal[]{documentBase, BigDecimal.ZERO, BigDecimal.ZERO};
        }
        for (Leg leg : legs) {
            BigDecimal share = documentBase.multiply(leg.amount())
                    .divide(lineTotal, 2, RoundingMode.HALF_UP);
            TaxTreatment treatment = leg.treatment() == null ? TaxTreatment.STANDARD : leg.treatment();
            switch (treatment) {
                case ZERO_RATED -> zeroRated = zeroRated.add(share);
                case EXEMPT -> exempt = exempt.add(share);
                default -> standard = standard.add(share);
            }
        }
        // Push any rounding remainder onto the standard bucket so the three add back exactly.
        BigDecimal drift = documentBase.subtract(standard).subtract(zeroRated).subtract(exempt);
        standard = standard.add(drift);
        return new BigDecimal[]{standard, zeroRated, exempt};
    }

    // ----- Tax liability -----

    @Transactional(readOnly = true)
    public TaxLiability taxLiability(LocalDate asOf) {
        Map<Long, BigDecimal[]> totals = new LinkedHashMap<>();
        for (Object[] row : journalLineRepository.postedTotalsUpTo(asOf)) {
            totals.put(((Number) row[0]).longValue(), new BigDecimal[]{
                    row[1] == null ? BigDecimal.ZERO : (BigDecimal) row[1],
                    row[2] == null ? BigDecimal.ZERO : (BigDecimal) row[2]});
        }

        List<TaxAccountBalance> balances = new ArrayList<>();
        BigDecimal payable = BigDecimal.ZERO;
        BigDecimal receivable = BigDecimal.ZERO;

        for (Account account : accountRepository.findAllByOrderByCodeAsc()) {
            if (!TAX_ACCOUNT_CODES.contains(account.getCode())) {
                continue;
            }
            BigDecimal[] dc = totals.getOrDefault(account.getId(),
                    new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            boolean debitNormal = account.getType() == AccountType.ASSET
                    || account.getType() == AccountType.EXPENSE;
            BigDecimal movement = debitNormal ? dc[0].subtract(dc[1]) : dc[1].subtract(dc[0]);
            BigDecimal balance = zero(account.getOpeningBalance()).add(movement);
            balances.add(new TaxAccountBalance(account.getId(), account.getCode(), account.getName(),
                    account.getType().name(), dc[0], dc[1], balance));
            if (debitNormal) {
                receivable = receivable.add(balance);
            } else {
                payable = payable.add(balance);
            }
        }
        return new TaxLiability(asOf, balances, payable, receivable, payable.subtract(receivable));
    }

    // ----- Tax transactions -----

    @Transactional(readOnly = true)
    public List<TaxTransaction> taxTransactions(LocalDate from, LocalDate to) {
        Map<Long, Account> taxAccounts = new LinkedHashMap<>();
        for (Account account : accountRepository.findAllByOrderByCodeAsc()) {
            if (TAX_ACCOUNT_CODES.contains(account.getCode())) {
                taxAccounts.put(account.getId(), account);
            }
        }
        List<TaxTransaction> rows = new ArrayList<>();
        for (JournalLine line : journalLineRepository.postedLinesBetween(from, to)) {
            Account account = taxAccounts.get(line.getAccountId());
            if (account == null) {
                continue;
            }
            rows.add(new TaxTransaction(
                    line.getEntry().getEntryDate(),
                    line.getEntry().getEntryNo(),
                    line.getEntry().getId(),
                    line.getEntry().getType().name(),
                    line.getEntry().getReference(),
                    account.getCode(),
                    account.getName(),
                    line.getMemo(),
                    line.getDebitValue(),
                    line.getCreditValue()));
        }
        rows.sort((a, b) -> a.date().compareTo(b.date()));
        return rows;
    }

    // ----- Records -----

    public record VatDocument(Long id, String documentNo, LocalDate date, String partyName,
                              BigDecimal taxableAmount, BigDecimal vatAmount, BigDecimal total) {}

    public record VatReturn(LocalDate from, LocalDate to,
                            List<VatDocument> sales, List<VatDocument> purchases,
                            BigDecimal taxableSales, BigDecimal zeroRatedSales, BigDecimal exemptSales,
                            BigDecimal outputVat,
                            BigDecimal taxablePurchases, BigDecimal zeroRatedPurchases,
                            BigDecimal exemptPurchases, BigDecimal inputVat,
                            BigDecimal netVat,
                            BigDecimal ledgerOutputVat, BigDecimal ledgerInputVat,
                            BigDecimal outputVariance, BigDecimal inputVariance) {

        public boolean payable() {
            return netVat.signum() >= 0;
        }

        public boolean reconciled() {
            return outputVariance.signum() == 0 && inputVariance.signum() == 0;
        }
    }

    public record TaxAccountBalance(Long accountId, String code, String name, String type,
                                    BigDecimal debits, BigDecimal credits, BigDecimal balance) {}

    public record TaxLiability(LocalDate asOf, List<TaxAccountBalance> balances,
                               BigDecimal totalPayable, BigDecimal totalReceivable,
                               BigDecimal netPosition) {}

    public record TaxTransaction(LocalDate date, String entryNo, Long entryId, String entryType,
                                 String reference, String accountCode, String accountName,
                                 String memo, BigDecimal debit, BigDecimal credit) {}
}
