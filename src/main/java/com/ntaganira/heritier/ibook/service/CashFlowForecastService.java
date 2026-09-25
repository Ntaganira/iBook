/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : CashFlowForecastService.java
 * - Date      : 2026. 09. 20.
 * - User      : Hntaganira
 * - Desc      : Projects the cash balance forward from what is owed and what is forecast
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.entity.Account;
import com.ntaganira.heritier.ibook.entity.Bill;
import com.ntaganira.heritier.ibook.entity.Forecast;
import com.ntaganira.heritier.ibook.entity.ForecastLine;
import com.ntaganira.heritier.ibook.entity.Invoice;
import com.ntaganira.heritier.ibook.repository.AccountRepository;
import com.ntaganira.heritier.ibook.repository.BillRepository;
import com.ntaganira.heritier.ibook.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CashFlowForecastService {

    private static final int MONTHS = 12;
    private static final int MAX_LAG = 6;

    private final AccountRepository accountRepository;
    private final InvoiceRepository invoiceRepository;
    private final BillRepository billRepository;
    private final ReportService reportService;

    public CashFlowForecastService(AccountRepository accountRepository,
                                   InvoiceRepository invoiceRepository,
                                   BillRepository billRepository,
                                   ReportService reportService) {
        this.accountRepository = accountRepository;
        this.invoiceRepository = invoiceRepository;
        this.billRepository = billRepository;
        this.reportService = reportService;
    }

    /**
     * Projects the cash balance month by month from three things that are already known or already
     * forecast: what is in the bank now, what customers and suppliers owe by when it falls due, and
     * what trading the revenue and expense forecasts expect.
     *
     * <p>Two deliberate positions. First, the opening balance is read through
     * {@link ReportService#signedBalancesUpTo}, so it is the same cash the balance sheet reports
     * rather than a second definition. Second, a forecast is an <em>accrual</em> figure — the month
     * a sale is earned, not the month it is paid for — so it is carried into cash by the collection
     * and payment lags rather than being treated as cash on the spot. Everything owed before the
     * forecast starts is already overdue, so it lands in the first month and is flagged, not spread
     * politely into the future.
     */
    @Transactional(readOnly = true)
    public CashFlow project(LocalDate startMonth, Forecast revenueForecast, Forecast expenseForecast,
                            int collectionLag, int paymentLag) {
        LocalDate start = startMonth == null
                ? LocalDate.now().withDayOfMonth(1) : startMonth.withDayOfMonth(1);
        LocalDate end = start.plusMonths(MONTHS).minusDays(1);
        int collect = clampLag(collectionLag);
        int pay = clampLag(paymentLag);

        Opening opening = openingCash(start.minusDays(1));

        BigDecimal[] fromDebtors = new BigDecimal[MONTHS];
        BigDecimal[] toCreditors = new BigDecimal[MONTHS];
        BigDecimal[] fromTrading = new BigDecimal[MONTHS];
        BigDecimal[] forTrading = new BigDecimal[MONTHS];
        for (int m = 0; m < MONTHS; m++) {
            fromDebtors[m] = BigDecimal.ZERO;
            toCreditors[m] = BigDecimal.ZERO;
            fromTrading[m] = BigDecimal.ZERO;
            forTrading[m] = BigDecimal.ZERO;
        }

        BigDecimal overdueIn = BigDecimal.ZERO;
        BigDecimal overdueOut = BigDecimal.ZERO;
        BigDecimal beyondIn = BigDecimal.ZERO;
        BigDecimal beyondOut = BigDecimal.ZERO;
        int debtorCount = 0;
        int creditorCount = 0;

        for (Invoice invoice : invoiceRepository.findOutstanding()) {
            BigDecimal owed = zero(invoice.getTotal())
                    .subtract(zero(invoice.getAmountPaid()))
                    .subtract(zero(invoice.getCreditedAmount()));
            if (owed.signum() <= 0) {
                continue;
            }
            debtorCount++;
            int slot = monthSlot(start, invoice.getDueDate());
            if (slot < 0) {
                fromDebtors[0] = fromDebtors[0].add(owed);
                overdueIn = overdueIn.add(owed);
            } else if (slot < MONTHS) {
                fromDebtors[slot] = fromDebtors[slot].add(owed);
            } else {
                beyondIn = beyondIn.add(owed);
            }
        }

        for (Bill bill : billRepository.findOutstanding()) {
            BigDecimal owed = zero(bill.getTotal()).subtract(zero(bill.getAmountPaid()));
            if (owed.signum() <= 0) {
                continue;
            }
            creditorCount++;
            int slot = monthSlot(start, bill.getDueDate());
            if (slot < 0) {
                toCreditors[0] = toCreditors[0].add(owed);
                overdueOut = overdueOut.add(owed);
            } else if (slot < MONTHS) {
                toCreditors[slot] = toCreditors[slot].add(owed);
            } else {
                beyondOut = beyondOut.add(owed);
            }
        }

        BigDecimal revenueAfterHorizon = spread(revenueForecast, start, collect, fromTrading);
        BigDecimal expenseAfterHorizon = spread(expenseForecast, start, pay, forTrading);

        List<MonthRow> rows = new ArrayList<>();
        BigDecimal balance = opening.total();
        BigDecimal totalIn = BigDecimal.ZERO;
        BigDecimal totalOut = BigDecimal.ZERO;
        BigDecimal lowest = null;
        int lowestMonth = 1;

        for (int m = 0; m < MONTHS; m++) {
            BigDecimal in = fromDebtors[m].add(fromTrading[m]);
            BigDecimal out = toCreditors[m].add(forTrading[m]);
            BigDecimal net = in.subtract(out);
            BigDecimal openingThisMonth = balance;
            balance = balance.add(net);
            totalIn = totalIn.add(in);
            totalOut = totalOut.add(out);
            if (lowest == null || balance.compareTo(lowest) < 0) {
                lowest = balance;
                lowestMonth = m + 1;
            }
            rows.add(new MonthRow(m + 1, start.plusMonths(m), openingThisMonth,
                    fromDebtors[m], fromTrading[m], toCreditors[m], forTrading[m],
                    in, out, net, balance));
        }

        return new CashFlow(start, end, collect, pay, opening, rows,
                totalIn, totalOut, totalIn.subtract(totalOut), balance,
                lowest == null ? BigDecimal.ZERO : lowest, lowestMonth,
                revenueForecast, expenseForecast,
                overdueIn, overdueOut, beyondIn, beyondOut,
                revenueAfterHorizon, expenseAfterHorizon,
                debtorCount, creditorCount);
    }

    /**
     * Carries a forecast month into the cash month it is expected to settle in. Anything pushed
     * past the twelfth month is returned rather than dropped, because a lag quietly swallowing the
     * last months of a forecast would make the projection look better than it is.
     */
    private static BigDecimal spread(Forecast forecast, LocalDate start, int lag, BigDecimal[] into) {
        if (forecast == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal beyond = BigDecimal.ZERO;
        for (int m = 1; m <= MONTHS; m++) {
            BigDecimal amount = BigDecimal.ZERO;
            for (ForecastLine line : forecast.getLines()) {
                amount = amount.add(line.amountForMonth(m));
            }
            if (amount.signum() == 0) {
                continue;
            }
            long offset = ChronoUnit.MONTHS.between(start, forecast.getStartDate().plusMonths(m - 1L))
                    + lag;
            if (offset < 0) {
                into[0] = into[0].add(amount);
            } else if (offset < MONTHS) {
                into[(int) offset] = into[(int) offset].add(amount);
            } else {
                beyond = beyond.add(amount);
            }
        }
        return beyond;
    }

    /** Cash and bank only, by the same chart convention the banking pages use. */
    private Opening openingCash(LocalDate asOf) {
        Map<Long, BigDecimal> balances = reportService.signedBalancesUpTo(asOf);
        List<OpeningAccount> accounts = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Account account : accountRepository.findAllByOrderByCodeAsc()) {
            if (!BankingService.isBankingAccount(account)) {
                continue;
            }
            BigDecimal balance = zero(balances.get(account.getId()));
            if (balance.signum() == 0) {
                continue;
            }
            accounts.add(new OpeningAccount(account.getId(), account.getCode(), account.getName(),
                    BankingService.kindOf(account).name(), balance));
            total = total.add(balance);
        }
        return new Opening(asOf, accounts, total);
    }

    /** Negative when it is already past due, which is a fact about the debt, not a month. */
    private static int monthSlot(LocalDate start, LocalDate dueDate) {
        if (dueDate == null) {
            return 0;
        }
        LocalDate dueMonth = dueDate.withDayOfMonth(1);
        return (int) ChronoUnit.MONTHS.between(start, dueMonth);
    }

    private static int clampLag(int lag) {
        return Math.max(0, Math.min(MAX_LAG, lag));
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public record OpeningAccount(Long accountId, String code, String name, String kind,
                                 BigDecimal balance) {}

    public record Opening(LocalDate asOf, List<OpeningAccount> accounts, BigDecimal total) {}

    public record MonthRow(int month, LocalDate monthStart, BigDecimal opening,
                           BigDecimal fromDebtors, BigDecimal fromTrading,
                           BigDecimal toCreditors, BigDecimal forTrading,
                           BigDecimal totalIn, BigDecimal totalOut,
                           BigDecimal net, BigDecimal closing) {
        public boolean isCashShort() {
            return closing.signum() < 0;
        }

        public boolean isDrawingDown() {
            return net.signum() < 0;
        }
    }

    public record CashFlow(LocalDate from, LocalDate to, int collectionLag, int paymentLag,
                           Opening opening, List<MonthRow> months,
                           BigDecimal totalIn, BigDecimal totalOut, BigDecimal netMovement,
                           BigDecimal closing, BigDecimal lowestBalance, int lowestMonth,
                           Forecast revenueForecast, Forecast expenseForecast,
                           BigDecimal overdueReceivable, BigDecimal overduePayable,
                           BigDecimal receivableAfterHorizon, BigDecimal payableAfterHorizon,
                           BigDecimal revenueAfterHorizon, BigDecimal expenseAfterHorizon,
                           int debtorInvoices, int creditorBills) {

        public boolean goesShort() {
            return lowestBalance.signum() < 0;
        }

        public boolean hasTradingForecast() {
            return revenueForecast != null || expenseForecast != null;
        }

        public boolean hasOverdue() {
            return overdueReceivable.signum() != 0 || overduePayable.signum() != 0;
        }

        public boolean hasBeyondHorizon() {
            return receivableAfterHorizon.signum() != 0 || payableAfterHorizon.signum() != 0
                    || revenueAfterHorizon.signum() != 0 || expenseAfterHorizon.signum() != 0;
        }
    }
}
