/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : RecurringJournalScheduler.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Nightly sweep that raises entries from due recurring journal schedules
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class RecurringJournalScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(RecurringJournalScheduler.class);

    private final RecurringJournalService recurringJournalService;

    public RecurringJournalScheduler(RecurringJournalService recurringJournalService) {
        this.recurringJournalService = recurringJournalService;
    }

    /**
     * Runs once a day, a little after the invoice sweep so the two are not competing for the
     * journal numbering sequence. An occurrence is owed on a date rather than at a time, and a
     * second pass on the same day finds nothing because generating moves the cycle on.
     */
    @Scheduled(cron = "0 45 2 * * *")
    public void generateDueEntries() {
        RecurringJournalService.RunReport report =
                recurringJournalService.runDue(LocalDate.now(), "system");
        if (!report.isEmpty() || report.hasFailures()) {
            LOG.info("Recurring journals: {} entries raised from {} schedule(s), {} skipped",
                    report.entries(), report.schedules(), report.failures().size());
        }
    }
}
