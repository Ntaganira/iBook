/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : ForecastService.java
 * - Date      : 2026. 09. 20.
 * - User      : Hntaganira
 * - Desc      : Revenue and expense forecasts worked out from posted history
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.ForecastForm;
import com.ntaganira.heritier.ibook.entity.Account;
import com.ntaganira.heritier.ibook.entity.Budget;
import com.ntaganira.heritier.ibook.entity.BudgetLine;
import com.ntaganira.heritier.ibook.entity.Company;
import com.ntaganira.heritier.ibook.entity.Forecast;
import com.ntaganira.heritier.ibook.entity.ForecastLine;
import com.ntaganira.heritier.ibook.enums.AccountType;
import com.ntaganira.heritier.ibook.enums.ForecastKind;
import com.ntaganira.heritier.ibook.enums.ForecastMethod;
import com.ntaganira.heritier.ibook.enums.ForecastStatus;
import com.ntaganira.heritier.ibook.repository.AccountRepository;
import com.ntaganira.heritier.ibook.repository.BudgetRepository;
import com.ntaganira.heritier.ibook.repository.CompanyRepository;
import com.ntaganira.heritier.ibook.repository.ForecastRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class ForecastService {

    private static final String MODULE = "forecasts";
    private static final int MONTHS = 12;
    private static final int MIN_BASIS = 1;
    private static final int MAX_BASIS = 36;

    private final ForecastRepository forecastRepository;
    private final AccountRepository accountRepository;
    private final BudgetRepository budgetRepository;
    private final CompanyRepository companyRepository;
    private final ReportService reportService;
    private final AuditService auditService;

    public ForecastService(ForecastRepository forecastRepository,
                           AccountRepository accountRepository,
                           BudgetRepository budgetRepository,
                           CompanyRepository companyRepository,
                           ReportService reportService,
                           AuditService auditService) {
        this.forecastRepository = forecastRepository;
        this.accountRepository = accountRepository;
        this.budgetRepository = budgetRepository;
        this.companyRepository = companyRepository;
        this.reportService = reportService;
        this.auditService = auditService;
    }

    // ----- Queries -----

    @Transactional(readOnly = true)
    public List<Forecast> list(ForecastKind kind) {
        return forecastRepository.findByKindOrderByStartDateDescIdDesc(kind);
    }

    @Transactional(readOnly = true)
    public Forecast get(Long id) {
        if (id == null) {
            return null;
        }
        Forecast forecast = forecastRepository.findById(id).orElse(null);
        if (forecast != null) {
            forecast.getLines().size();
        }
        return forecast;
    }

    /**
     * The forecast a cash flow reads when nobody names one: the published one, or failing that the
     * most recent draft, so a page is useful before anyone has committed to a set of figures.
     */
    @Transactional(readOnly = true)
    public Forecast current(ForecastKind kind) {
        Optional<Forecast> published = forecastRepository
                .findFirstByKindAndStatusOrderByStartDateDescIdDesc(kind, ForecastStatus.PUBLISHED);
        if (published.isPresent()) {
            return get(published.get().getId());
        }
        for (Forecast forecast : forecastRepository.findByKindOrderByStartDateDescIdDesc(kind)) {
            if (!forecast.isArchived()) {
                return get(forecast.getId());
            }
        }
        return null;
    }

    @Transactional(readOnly = true)
    public String baseCurrency() {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElse(null);
        return company == null || company.getCurrencyCode() == null ? "RWF" : company.getCurrencyCode();
    }

    @Transactional(readOnly = true)
    public boolean nameExists(String name, Long id) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return id == null
                ? forecastRepository.existsByNameIgnoreCase(name.trim())
                : forecastRepository.existsByNameIgnoreCaseAndIdNot(name.trim(), id);
    }

    /** A forecast of money coming in can only name revenue accounts, and the reverse. */
    @Transactional(readOnly = true)
    public List<Account> forecastableAccounts(ForecastKind kind) {
        AccountType wanted = typeFor(kind);
        List<Account> rows = new ArrayList<>();
        for (Account account : accountRepository.findAllByOrderByCodeAsc()) {
            if (account.isActive() && account.getType() == wanted) {
                rows.add(account);
            }
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<Budget> budgetsForSource() {
        return budgetRepository.findAllByOrderByFiscalYearDescNameAsc();
    }

    @Transactional(readOnly = true)
    public ForecastSummary summary(ForecastKind kind) {
        List<Forecast> rows = forecastRepository.findByKindOrderByStartDateDescIdDesc(kind);
        BigDecimal publishedTotal = BigDecimal.ZERO;
        for (Forecast forecast : rows) {
            if (forecast.isPublished()) {
                publishedTotal = publishedTotal.add(zero(forecast.getTotalAmount()));
            }
        }
        return new ForecastSummary(rows.size(),
                forecastRepository.countByKindAndStatus(kind, ForecastStatus.DRAFT),
                forecastRepository.countByKindAndStatus(kind, ForecastStatus.PUBLISHED),
                forecastRepository.countByKindAndStatus(kind, ForecastStatus.ARCHIVED),
                publishedTotal);
    }

    /**
     * The forecast alongside what the same accounts actually did. A forecast on its own is a claim;
     * next to the history it was drawn from it is an argument.
     *
     * <p>The two have to be put on the same footing first. A forecast always covers twelve months,
     * but the history behind it may be six, so comparing the raw totals would report a doubling
     * where nothing changed at all. The history is scaled to the same twelve months before any
     * change is worked out, and the page says over how many months it was really measured.
     */
    @Transactional(readOnly = true)
    public ForecastView view(Forecast forecast) {
        if (forecast == null) {
            return null;
        }
        List<BigDecimal> monthTotals = new ArrayList<>();
        for (int m = 1; m <= MONTHS; m++) {
            BigDecimal total = BigDecimal.ZERO;
            for (ForecastLine line : forecast.getLines()) {
                total = total.add(line.amountForMonth(m));
            }
            monthTotals.add(total);
        }
        BigDecimal history = BigDecimal.ZERO;
        for (ForecastLine line : forecast.getLines()) {
            history = history.add(zero(line.getHistoryAmount()));
        }
        int historyMonths = forecast.getHistoryMonths();
        BigDecimal annualised = history
                .multiply(new BigDecimal(MONTHS))
                .divide(new BigDecimal(historyMonths), 2, RoundingMode.HALF_UP);

        BigDecimal total = zero(forecast.getTotalAmount());
        BigDecimal change = total.subtract(annualised);
        BigDecimal percent = annualised.signum() == 0 ? BigDecimal.ZERO
                : change.multiply(new BigDecimal("100"))
                        .divide(annualised.abs(), 1, RoundingMode.HALF_UP);
        return new ForecastView(forecast, monthTotals, history, historyMonths, annualised,
                change, percent);
    }

    // ----- Generation -----

    /**
     * Fills the grid from what the ledger already did. This is a visible action rather than
     * something save does quietly, because a forecast somebody adjusted by hand and a forecast the
     * model wrote are different claims, and only the person at the keyboard knows which one is
     * meant.
     *
     * <p>Every method here floors its result at zero. A trend line extended far enough goes
     * negative, and an account whose history nets to a credit would otherwise forecast negative
     * spend — neither is a statement anyone means to make, so the page reports how many accounts
     * were floored rather than hiding it.
     */
    @Transactional(readOnly = true)
    public Generation generate(ForecastForm form) {
        ForecastKind kind = parseKind(form.getKind());
        ForecastMethod method = parseMethod(form.getMethod());
        LocalDate start = firstOfMonth(form.getStartDate());
        int basis = Math.max(MIN_BASIS, Math.min(MAX_BASIS, form.basisMonthsValue()));
        BigDecimal uplift = BigDecimal.ONE.add(
                form.growthPercentValue().divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));

        Budget budget = method == ForecastMethod.BUDGET && form.getSourceBudgetId() != null
                ? budgetRepository.findById(form.getSourceBudgetId()).orElse(null)
                : null;
        if (method == ForecastMethod.BUDGET && budget == null) {
            throw new IllegalArgumentException("Choose the budget the figures should be copied from");
        }
        if (budget != null) {
            budget.getLines().size();
        }

        LocalDate historyFrom = historyStart(method, start, basis);
        int historyMonths = historyMonths(method, basis);
        Map<Integer, Map<Long, BigDecimal>> history = monthlyMovements(historyFrom, historyMonths);

        List<Account> accounts = forecastableAccounts(kind);
        Set<Long> onForm = new HashSet<>();
        for (ForecastForm.Line row : form.getLines()) {
            if (row != null && !row.isEmpty()) {
                onForm.add(row.getAccountId());
            }
        }

        List<ForecastForm.Line> built = new ArrayList<>();
        int floored = 0;
        int noHistory = 0;

        for (Account account : accounts) {
            List<BigDecimal> series = seriesFor(history, account.getId(), historyMonths);
            BigDecimal historyTotal = sum(series);
            boolean traded = historyTotal.signum() != 0;
            boolean budgeted = budget != null && budgetAmountsFor(budget, account.getId(), start) != null;
            if (!traded && !budgeted && !onForm.contains(account.getId())) {
                continue;
            }
            if (!traded && method.usesHistory()) {
                noHistory++;
            }

            ForecastForm.Line row = new ForecastForm.Line();
            row.setAccountId(account.getId());
            row.setHistoryAmount(historyTotal);

            BigDecimal[] months = switch (method) {
                case AVERAGE -> flat(series, historyMonths);
                case TREND -> trend(series);
                case SEASONAL -> seasonal(series);
                case BUDGET -> fromBudget(budget, account.getId(), start);
                case MANUAL -> new BigDecimal[MONTHS];
            };

            BigDecimal annual = BigDecimal.ZERO;
            boolean flooredHere = false;
            for (int m = 1; m <= MONTHS; m++) {
                BigDecimal value = months[m - 1] == null ? BigDecimal.ZERO : months[m - 1];
                if (method != ForecastMethod.BUDGET && method != ForecastMethod.MANUAL) {
                    value = value.multiply(uplift);
                }
                value = value.setScale(2, RoundingMode.HALF_UP);
                if (value.signum() < 0) {
                    value = BigDecimal.ZERO;
                    flooredHere = true;
                }
                row.setMonthAt(m, value);
                annual = annual.add(value);
            }
            if (flooredHere) {
                floored++;
            }
            row.setGeneratedAmount(annual);
            built.add(row);
        }

        if (method == ForecastMethod.MANUAL) {
            // Nothing to work out, but the accounts and their history are still worth laying out.
            for (ForecastForm.Line row : built) {
                row.setGeneratedAmount(BigDecimal.ZERO);
            }
        }

        applyBuiltLines(form, built);
        return new Generation(method, kind, historyFrom, historyFrom.plusMonths(historyMonths).minusDays(1),
                built.size(), floored, noHistory);
    }

    /** Keeps the notes anyone had already typed against an account when the grid is rebuilt. */
    private void applyBuiltLines(ForecastForm form, List<ForecastForm.Line> built) {
        Map<Long, String> notes = new LinkedHashMap<>();
        for (ForecastForm.Line row : form.getLines()) {
            if (row != null && !row.isEmpty() && trimToNull(row.getNotes()) != null) {
                notes.put(row.getAccountId(), row.getNotes());
            }
        }
        form.getLines().clear();
        int index = 0;
        for (ForecastForm.Line row : built) {
            row.setNotes(notes.get(row.getAccountId()));
            ForecastForm.Line target = form.getLines().get(index++);
            copy(row, target);
        }
        for (int i = 0; i < 3; i++) {
            form.getLines().get(index++);
        }
    }

    private static void copy(ForecastForm.Line from, ForecastForm.Line to) {
        to.setAccountId(from.getAccountId());
        to.setAnnualAmount(null);
        for (int m = 1; m <= MONTHS; m++) {
            to.setMonthAt(m, from.monthAt(m));
        }
        to.setGeneratedAmount(from.getGeneratedAmount());
        to.setHistoryAmount(from.getHistoryAmount());
        to.setNotes(from.getNotes());
    }

    private static LocalDate historyStart(ForecastMethod method, LocalDate start, int basis) {
        return start.minusMonths(historyMonths(method, basis));
    }

    /**
     * How far back the comparison reads. The average and trend methods read exactly the basis they
     * were given; everything else is shown against the twelve months before it starts, because a
     * copied budget and a hand-typed figure have no basis of their own to be judged against.
     * {@link Forecast#getHistoryMonths()} has to agree with this, or a saved forecast would be
     * scaled against a window it was never measured over.
     */
    private static int historyMonths(ForecastMethod method, int basis) {
        return method.usesHistory() && method != ForecastMethod.SEASONAL ? basis : MONTHS;
    }

    /** One grouped query per month, so a figure here is the same figure the ledger reports. */
    private Map<Integer, Map<Long, BigDecimal>> monthlyMovements(LocalDate from, int months) {
        Map<Integer, Map<Long, BigDecimal>> byMonth = new LinkedHashMap<>();
        for (int i = 0; i < months; i++) {
            LocalDate monthStart = from.plusMonths(i);
            LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
            byMonth.put(i, reportService.signedMovementsBetween(monthStart, monthEnd));
        }
        return byMonth;
    }

    private static List<BigDecimal> seriesFor(Map<Integer, Map<Long, BigDecimal>> history,
                                              Long accountId, int months) {
        List<BigDecimal> series = new ArrayList<>();
        for (int i = 0; i < months; i++) {
            Map<Long, BigDecimal> month = history.get(i);
            series.add(month == null ? BigDecimal.ZERO : zero(month.get(accountId)));
        }
        return series;
    }

    /** The plain average of the basis months, repeated across all twelve. */
    private static BigDecimal[] flat(List<BigDecimal> series, int months) {
        BigDecimal average = months == 0 ? BigDecimal.ZERO
                : sum(series).divide(new BigDecimal(months), 2, RoundingMode.HALF_UP);
        BigDecimal[] out = new BigDecimal[MONTHS];
        for (int m = 0; m < MONTHS; m++) {
            out[m] = average;
        }
        return out;
    }

    /**
     * A least squares straight line through the basis months, carried forward. Two months is the
     * fewest that can describe a direction; one month has none, so it falls back to a flat line.
     */
    private static BigDecimal[] trend(List<BigDecimal> series) {
        int n = series.size();
        if (n < 2) {
            return flat(series, Math.max(1, n));
        }
        double sumX = 0;
        double sumY = 0;
        double sumXy = 0;
        double sumXx = 0;
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = series.get(i).doubleValue();
            sumX += x;
            sumY += y;
            sumXy += x * y;
            sumXx += x * x;
        }
        double denominator = n * sumXx - sumX * sumX;
        if (denominator == 0) {
            return flat(series, n);
        }
        double slope = (n * sumXy - sumX * sumY) / denominator;
        double intercept = (sumY - slope * sumX) / n;

        BigDecimal[] out = new BigDecimal[MONTHS];
        for (int m = 0; m < MONTHS; m++) {
            double projected = intercept + slope * (n + m);
            out[m] = BigDecimal.valueOf(projected).setScale(2, RoundingMode.HALF_UP);
        }
        return out;
    }

    /** The same calendar month a year earlier, so a trade with a season keeps its shape. */
    private static BigDecimal[] seasonal(List<BigDecimal> series) {
        BigDecimal[] out = new BigDecimal[MONTHS];
        for (int m = 0; m < MONTHS; m++) {
            out[m] = m < series.size() ? series.get(m) : BigDecimal.ZERO;
        }
        return out;
    }

    private static BigDecimal[] fromBudget(Budget budget, Long accountId, LocalDate start) {
        BigDecimal[] amounts = budgetAmountsFor(budget, accountId, start);
        return amounts == null ? new BigDecimal[MONTHS] : amounts;
    }

    /**
     * Budget figures lined up by calendar month rather than by column. A budget starting in July
     * and a forecast starting in January both hold twelve columns, and taking the first column of
     * one as the first column of the other would silently shift every figure six months.
     */
    private static BigDecimal[] budgetAmountsFor(Budget budget, Long accountId, LocalDate start) {
        BudgetLine match = null;
        for (BudgetLine line : budget.getLines()) {
            if (accountId.equals(line.getAccountId())) {
                match = line;
                break;
            }
        }
        if (match == null) {
            return null;
        }
        BigDecimal[] out = new BigDecimal[MONTHS];
        for (int m = 0; m < MONTHS; m++) {
            LocalDate month = start.plusMonths(m);
            long offset = ChronoUnit.MONTHS.between(budget.getStartDate(), month);
            out[m] = offset >= 0 && offset < MONTHS
                    ? match.amountForMonth((int) offset + 1)
                    : BigDecimal.ZERO;
        }
        return out;
    }

    // ----- Create / update -----

    @Transactional
    public Forecast save(ForecastForm form, Long id, String username) {
        Forecast forecast;
        if (id == null) {
            forecast = new Forecast();
            forecast.setCreatedBy(username);
            forecast.setStatus(ForecastStatus.DRAFT);
            forecast.setKind(parseKind(form.getKind()));
        } else {
            forecast = forecastRepository.findById(id).orElseThrow();
            if (!forecast.isEditable()) {
                throw new IllegalStateException("An archived forecast cannot be edited");
            }
        }

        if (trimToNull(form.getName()) == null) {
            throw new IllegalArgumentException("A forecast needs a name");
        }
        LocalDate start = firstOfMonth(form.getStartDate());
        ForecastMethod method = parseMethod(form.getMethod());

        forecast.setName(form.getName().trim());
        forecast.setStartDate(start);
        forecast.setMethod(method);
        forecast.setBasisMonths(Math.max(MIN_BASIS, Math.min(MAX_BASIS, form.basisMonthsValue())));
        forecast.setGrowthPercent(form.growthPercentValue());
        forecast.setDescription(trimToNull(form.getDescription()));
        forecast.setNotes(trimToNull(form.getNotes()));

        if (method == ForecastMethod.BUDGET) {
            Budget budget = form.getSourceBudgetId() == null ? null
                    : budgetRepository.findById(form.getSourceBudgetId()).orElse(null);
            if (budget == null) {
                throw new IllegalArgumentException("Choose the budget the figures were copied from");
            }
            forecast.setSourceBudgetId(budget.getId());
            forecast.setSourceBudgetName(budget.getName());
        } else {
            forecast.setSourceBudgetId(null);
            forecast.setSourceBudgetName(null);
        }

        applyLines(forecast, form);

        Forecast saved = forecastRepository.save(forecast);
        auditService.log(MODULE, id == null ? "CREATE_FORECAST" : "UPDATE_FORECAST",
                "forecast#" + saved.getId(), saved.getName());

        if (form.isPublishNow() && saved.isDraft()) {
            saved = publish(saved.getId(), username);
        }
        return saved;
    }

    private void applyLines(Forecast forecast, ForecastForm form) {
        forecast.getLines().clear();
        AccountType wanted = typeFor(forecast.getKind());
        Set<Long> seen = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        int sort = 0;

        for (ForecastForm.Line row : form.getLines()) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            Account account = accountRepository.findById(row.getAccountId()).orElse(null);
            if (account == null) {
                continue;
            }
            if (account.getType() != wanted) {
                throw new IllegalArgumentException(account.getCode() + " " + account.getName()
                        + " is not " + (wanted == AccountType.REVENUE ? "a revenue" : "an expense")
                        + " account");
            }
            if (!seen.add(account.getId())) {
                throw new IllegalArgumentException(account.getCode() + " " + account.getName()
                        + " is forecast twice");
            }

            ForecastLine line = new ForecastLine();
            line.setAccountId(account.getId());
            line.setAccountCode(account.getCode());
            line.setAccountName(account.getName());
            line.setAccountType(account.getType());
            line.setNotes(trimToNull(row.getNotes()));
            line.setSortOrder(sort++);
            line.setHistoryAmount(zero(row.getHistoryAmount()));

            if (row.hasMonthlyDetail()) {
                for (int m = 1; m <= MONTHS; m++) {
                    line.setAmountForMonth(m, zero(row.monthAt(m)));
                }
            } else {
                spreadEvenly(line, zero(row.getAnnualAmount()));
            }
            BigDecimal annual = line.getMonthlyTotal();
            if (annual.signum() < 0) {
                throw new IllegalArgumentException(account.getCode() + " " + account.getName()
                        + " is forecast at a negative figure");
            }
            line.setAnnualAmount(annual);
            line.setGeneratedAmount(zero(row.getGeneratedAmount()));
            total = total.add(annual);
            forecast.addLine(line);
        }

        if (forecast.getLines().isEmpty()) {
            throw new IllegalArgumentException("A forecast needs at least one account on it");
        }
        forecast.setTotalAmount(total);
    }

    /** Same rule the budget grid uses: the months win, and the drift lands on the last one. */
    private static void spreadEvenly(ForecastLine line, BigDecimal annual) {
        if (annual.signum() == 0) {
            for (int m = 1; m <= MONTHS; m++) {
                line.setAmountForMonth(m, BigDecimal.ZERO);
            }
            return;
        }
        BigDecimal each = annual.divide(new BigDecimal(MONTHS), 2, RoundingMode.HALF_UP);
        BigDecimal running = BigDecimal.ZERO;
        for (int m = 1; m < MONTHS; m++) {
            line.setAmountForMonth(m, each);
            running = running.add(each);
        }
        line.setAmountForMonth(MONTHS, annual.subtract(running));
    }

    // ----- Lifecycle -----

    /**
     * Only one forecast of each kind is published at a time. A cash flow built on two different
     * sets of figures for the same months is not a forecast, it is a coin toss, so publishing one
     * puts the other back to draft rather than letting both claim to be current.
     */
    @Transactional
    public Forecast publish(Long id, String username) {
        Forecast forecast = forecastRepository.findById(id).orElseThrow();
        if (forecast.isArchived()) {
            throw new IllegalStateException("An archived forecast cannot be published");
        }
        if (forecast.isPublished()) {
            throw new IllegalStateException("This forecast is already published");
        }
        for (Forecast other : forecastRepository.othersWithStatus(forecast.getKind(),
                ForecastStatus.PUBLISHED, id)) {
            other.setStatus(ForecastStatus.DRAFT);
            other.setPublishedAt(null);
            other.setPublishedBy(null);
            forecastRepository.save(other);
            auditService.log(MODULE, "UNPUBLISH_FORECAST", "forecast#" + other.getId(),
                    other.getName());
        }
        forecast.setStatus(ForecastStatus.PUBLISHED);
        forecast.setPublishedAt(LocalDateTime.now());
        forecast.setPublishedBy(username);
        Forecast saved = forecastRepository.save(forecast);
        auditService.log(MODULE, "PUBLISH_FORECAST", "forecast#" + id, saved.getName());
        return saved;
    }

    @Transactional
    public Forecast unpublish(Long id) {
        Forecast forecast = forecastRepository.findById(id).orElseThrow();
        if (!forecast.isPublished()) {
            throw new IllegalStateException("Only a published forecast can be put back to draft");
        }
        forecast.setStatus(ForecastStatus.DRAFT);
        forecast.setPublishedAt(null);
        forecast.setPublishedBy(null);
        Forecast saved = forecastRepository.save(forecast);
        auditService.log(MODULE, "UNPUBLISH_FORECAST", "forecast#" + id, saved.getName());
        return saved;
    }

    @Transactional
    public Forecast archive(Long id) {
        Forecast forecast = forecastRepository.findById(id).orElseThrow();
        if (forecast.isArchived()) {
            throw new IllegalStateException("This forecast is already archived");
        }
        forecast.setStatus(ForecastStatus.ARCHIVED);
        Forecast saved = forecastRepository.save(forecast);
        auditService.log(MODULE, "ARCHIVE_FORECAST", "forecast#" + id, saved.getName());
        return saved;
    }

    @Transactional
    public Forecast restore(Long id) {
        Forecast forecast = forecastRepository.findById(id).orElseThrow();
        if (!forecast.isArchived()) {
            throw new IllegalStateException("Only an archived forecast can be restored");
        }
        forecast.setStatus(ForecastStatus.DRAFT);
        Forecast saved = forecastRepository.save(forecast);
        auditService.log(MODULE, "RESTORE_FORECAST", "forecast#" + id, saved.getName());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Forecast forecast = forecastRepository.findById(id).orElse(null);
        if (forecast == null) {
            return;
        }
        if (forecast.isPublished()) {
            throw new IllegalStateException("A published forecast has to be put back to draft first");
        }
        forecastRepository.delete(forecast);
        auditService.log(MODULE, "DELETE_FORECAST", "forecast#" + id, forecast.getName());
    }

    // ----- Helpers -----

    public static AccountType typeFor(ForecastKind kind) {
        return kind == ForecastKind.REVENUE ? AccountType.REVENUE : AccountType.EXPENSE;
    }

    public static ForecastKind parseKind(String kind) {
        if (kind == null || kind.isBlank()) {
            return ForecastKind.REVENUE;
        }
        try {
            return ForecastKind.valueOf(kind.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ForecastKind.REVENUE;
        }
    }

    public static ForecastMethod parseMethod(String method) {
        if (method == null || method.isBlank()) {
            return ForecastMethod.AVERAGE;
        }
        try {
            return ForecastMethod.valueOf(method.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ForecastMethod.AVERAGE;
        }
    }

    private static LocalDate firstOfMonth(LocalDate date) {
        return date == null
                ? LocalDate.now().withDayOfMonth(1).plusMonths(1)
                : date.withDayOfMonth(1);
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            total = total.add(zero(value));
        }
        return total;
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

    public record Generation(ForecastMethod method, ForecastKind kind,
                             LocalDate historyFrom, LocalDate historyTo,
                             int accounts, int floored, int withoutHistory) {
        public boolean hasWarnings() {
            return floored > 0 || withoutHistory > 0;
        }
    }

    public record ForecastView(Forecast forecast, List<BigDecimal> monthTotals,
                               BigDecimal historyTotal, int historyMonths,
                               BigDecimal historyAnnualised, BigDecimal changeOnHistory,
                               BigDecimal changePercent) {
        /** True when the history already covers twelve months and needed no scaling. */
        public boolean isFullYearOfHistory() {
            return historyMonths == 12;
        }

        public boolean isUp() {
            return changeOnHistory.signum() > 0;
        }

        public boolean isDown() {
            return changeOnHistory.signum() < 0;
        }
    }

    public record ForecastSummary(long all, long draft, long published, long archived,
                                  BigDecimal publishedTotal) {}
}
