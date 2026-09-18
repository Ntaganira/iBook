/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : RecurringInvoiceScheduler.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Nightly sweep that raises invoices from due recurring schedules
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class RecurringInvoiceScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(RecurringInvoiceScheduler.class);

    private final RecurringInvoiceService recurringInvoiceService;

    public RecurringInvoiceScheduler(RecurringInvoiceService recurringInvoiceService) {
        this.recurringInvoiceService = recurringInvoiceService;
    }

    /**
     * Runs once a day rather than on an interval: an occurrence is owed on a date, not at a time,
     * and a second pass on the same day finds nothing due because generating moves the cycle on.
     */
    @Scheduled(cron = "0 15 2 * * *")
    public void generateDueInvoices() {
        RecurringInvoiceService.RunReport report =
                recurringInvoiceService.runDue(LocalDate.now(), "system");
        if (!report.isEmpty() || report.hasFailures()) {
            LOG.info("Recurring invoices: {} raised from {} schedule(s), {} skipped",
                    report.invoices(), report.schedules(), report.failures().size());
        }
    }
}
