/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : TimesheetController.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Timesheets and billable time web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.TimeBillingForm;
import com.ntaganira.heritier.ibook.dto.TimeEntryForm;
import com.ntaganira.heritier.ibook.entity.TimeEntry;
import com.ntaganira.heritier.ibook.enums.TimeEntryStatus;
import com.ntaganira.heritier.ibook.repository.TaxRateRepository;
import com.ntaganira.heritier.ibook.service.AuditService;
import com.ntaganira.heritier.ibook.service.ProjectService;
import com.ntaganira.heritier.ibook.service.TimeEntryService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/projects")
public class TimesheetController {

    private static final int PAGE_SIZE = 25;

    private final TimeEntryService timeEntryService;
    private final ProjectService projectService;
    private final TaxRateRepository taxRateRepository;
    private final MessageSource messageSource;

    public TimesheetController(TimeEntryService timeEntryService,
                               ProjectService projectService,
                               TaxRateRepository taxRateRepository,
                               MessageSource messageSource) {
        this.timeEntryService = timeEntryService;
        this.projectService = projectService;
        this.taxRateRepository = taxRateRepository;
        this.messageSource = messageSource;
    }

    private String msg(String key) {
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
    }

    private Map<String, String> flash(String key, String detail) {
        return Map.of("title", msg(key), "detail", detail == null ? "" : detail, "type", "success");
    }

    private Map<String, String> errorFlash(String key, String detail) {
        return Map.of("title", msg(key), "detail", detail == null ? "" : detail, "type", "error");
    }

    private void rejectWithReason(BindingResult br, RuntimeException ex) {
        String reason = ex.getMessage() == null ? msg("tsh.actionFailed") : ex.getMessage();
        br.reject("tsh.failedDetail", new Object[]{reason}, reason);
    }

    /** Shared with the project page, which lists the same entries under a job. */
    static Map<String, String> timeStatusLabels(MessageSource messageSource) {
        Map<String, String> m = new LinkedHashMap<>();
        for (TimeEntryStatus s : TimeEntryStatus.values()) {
            m.put(s.name(), messageSource.getMessage("tsh.status." + s.name().toLowerCase(Locale.ROOT),
                    null, LocaleContextHolder.getLocale()));
        }
        return m;
    }

    private void addFormContext(Model model, String mode, Long editingId, Long currentProjectId) {
        model.addAttribute("mode", mode);
        model.addAttribute("editingId", editingId);
        model.addAttribute("projects", projectService.projectsForPicker(currentProjectId));
        model.addAttribute("people", timeEntryService.people());
        model.addAttribute("baseCurrency", projectService.baseCurrency());
    }

    // ----- Timesheets -----

    @GetMapping("/timesheets")
    public String timesheets(@RequestParam(value = "q", required = false) String q,
                             @RequestParam(value = "status", required = false) String status,
                             @RequestParam(value = "project", required = false) Long projectId,
                             @RequestParam(value = "person", required = false) String person,
                             @RequestParam(value = "from", required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(value = "to", required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                             @RequestParam(value = "sort", defaultValue = "workDate") String sort,
                             @RequestParam(value = "dir", defaultValue = "desc") String dir,
                             @RequestParam(value = "page", defaultValue = "0") int page,
                             Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "workDate", "workDate", "person", "projectCode",
                "hours", "billableAmount", "costAmount", "status");
        model.addAttribute("entries", timeEntryService.list(q, status, projectId, person, from, to,
                PageRequest.of(page, PAGE_SIZE, sp.sort())));
        model.addAttribute("summary", timeEntryService.summary());
        model.addAttribute("statusLabels", timeStatusLabels(messageSource));
        model.addAttribute("projects", projectService.all());
        model.addAttribute("people", timeEntryService.people());
        model.addAttribute("baseCurrency", projectService.baseCurrency());
        model.addAttribute("q", q);
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("projectId", projectId);
        model.addAttribute("person", person == null ? "" : person);
        model.addAttribute("from", from);
        model.addAttribute("to", to);

        StringBuilder fq = new StringBuilder();
        appendParam(fq, "q", q == null ? null : UriUtils.encodeQueryParam(q, StandardCharsets.UTF_8));
        appendParam(fq, "status", status);
        appendParam(fq, "project", projectId == null ? null : String.valueOf(projectId));
        appendParam(fq, "person", person == null ? null
                : UriUtils.encodeQueryParam(person, StandardCharsets.UTF_8));
        appendParam(fq, "from", from == null ? null : from.toString());
        appendParam(fq, "to", to == null ? null : to.toString());
        SortSpec.addListContext(model, "/projects/timesheets",
                fq.isEmpty() ? "" : "?" + fq, sp);
        return "projects/timesheets";
    }

    private static void appendParam(StringBuilder fq, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!fq.isEmpty()) {
            fq.append('&');
        }
        fq.append(name).append('=').append(value);
    }

    @GetMapping("/timesheets/new")
    public String newEntry(@RequestParam(value = "project", required = false) Long projectId,
                           Model model) {
        addFormContext(model, "create", null, projectId);
        TimeEntryForm empty = TimeEntryForm.empty();
        model.addAttribute("form", projectId == null ? empty
                : new TimeEntryForm(projectId, empty.person(), empty.workDate(), empty.hours(),
                        empty.task(), empty.description(), empty.billable(), empty.billRate(),
                        empty.costRate(), empty.approveNow()));
        return "projects/timesheet-form";
    }

    @GetMapping("/timesheets/{id}/edit")
    public String editEntry(@PathVariable Long id, Model model, RedirectAttributes ra) {
        TimeEntry entry = timeEntryService.get(id);
        if (entry == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("tsh.notFound", null));
            return "redirect:/projects/timesheets";
        }
        if (!entry.isEditable()) {
            ra.addFlashAttribute("flashMessage", errorFlash("tsh.notEditable", entry.getInvoiceNo()));
            return "redirect:/projects/timesheets";
        }
        addFormContext(model, "edit", id, entry.getProjectId());
        model.addAttribute("form", new TimeEntryForm(entry.getProjectId(), entry.getPerson(),
                entry.getWorkDate(), entry.getHours(), entry.getTask(), entry.getDescription(),
                entry.isBillable(), entry.getBillRate(), entry.getCostRate(), Boolean.FALSE));
        return "projects/timesheet-form";
    }

    @PostMapping("/timesheets")
    public String createEntry(@ModelAttribute("form") TimeEntryForm form, BindingResult br,
                              Model model, RedirectAttributes ra) {
        validate(form, br);
        if (br.hasErrors()) {
            addFormContext(model, "create", null, form.projectId());
            return "projects/timesheet-form";
        }
        try {
            TimeEntry saved = timeEntryService.save(form, null, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("tsh.saved",
                    saved.getProjectCode() + " · " + saved.getHours() + "h"));
            return "redirect:/projects/timesheets";
        } catch (RuntimeException ex) {
            rejectWithReason(br, ex);
            addFormContext(model, "create", null, form.projectId());
            return "projects/timesheet-form";
        }
    }

    @PostMapping("/timesheets/{id}")
    public String updateEntry(@PathVariable Long id, @ModelAttribute("form") TimeEntryForm form,
                              BindingResult br, Model model, RedirectAttributes ra) {
        if (timeEntryService.get(id) == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("tsh.notFound", null));
            return "redirect:/projects/timesheets";
        }
        validate(form, br);
        if (br.hasErrors()) {
            addFormContext(model, "edit", id, form.projectId());
            return "projects/timesheet-form";
        }
        try {
            TimeEntry saved = timeEntryService.save(form, id, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("tsh.saved",
                    saved.getProjectCode() + " · " + saved.getHours() + "h"));
            return "redirect:/projects/timesheets";
        } catch (RuntimeException ex) {
            rejectWithReason(br, ex);
            addFormContext(model, "edit", id, form.projectId());
            return "projects/timesheet-form";
        }
    }

    private void validate(TimeEntryForm form, BindingResult br) {
        if (form.projectId() == null) {
            br.rejectValue("projectId", "tsh.projectRequired");
        }
        if (form.person() == null || form.person().isBlank()) {
            br.rejectValue("person", "tsh.personRequired");
        }
        if (form.workDate() == null) {
            br.rejectValue("workDate", "tsh.dateRequired");
        }
        if (form.hours() == null || form.hours().signum() <= 0) {
            br.rejectValue("hours", "tsh.hoursRequired");
        }
    }

    // ----- Approval -----

    @PostMapping("/timesheets/{id}/approve")
    public String approve(@PathVariable Long id, RedirectAttributes ra) {
        return entryAction(id, ra, "tsh.approved",
                () -> timeEntryService.approve(id, AuditService.currentUsername()));
    }

    @PostMapping("/timesheets/{id}/reject")
    public String reject(@PathVariable Long id,
                         @RequestParam(value = "reason", required = false) String reason,
                         RedirectAttributes ra) {
        return entryAction(id, ra, "tsh.rejected", () -> timeEntryService.reject(id, reason));
    }

    @PostMapping("/timesheets/{id}/reopen")
    public String reopenEntry(@PathVariable Long id, RedirectAttributes ra) {
        return entryAction(id, ra, "tsh.reopened", () -> timeEntryService.reopen(id));
    }

    private String entryAction(Long id, RedirectAttributes ra, String successKey,
                               java.util.function.Supplier<TimeEntry> action) {
        try {
            action.get();
            ra.addFlashAttribute("flashMessage", flash(successKey, null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("tsh.actionFailed", ex.getMessage()));
        }
        return "redirect:/projects/timesheets";
    }

    @PostMapping("/timesheets/{id}/delete")
    public String deleteEntry(@PathVariable Long id, RedirectAttributes ra) {
        try {
            timeEntryService.delete(id);
            ra.addFlashAttribute("flashMessage", flash("tsh.deleted", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("tsh.actionFailed", ex.getMessage()));
        }
        return "redirect:/projects/timesheets";
    }

    @PostMapping("/timesheets/approve")
    public String approveSelected(@RequestParam(value = "entryIds", required = false) List<Long> ids,
                                  RedirectAttributes ra) {
        try {
            int done = timeEntryService.approveAll(ids, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("tsh.approvedMany", String.valueOf(done)));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("tsh.actionFailed", ex.getMessage()));
        }
        return "redirect:/projects/timesheets";
    }

    // ----- Billable time -----

    @GetMapping("/billable")
    public String billable(@RequestParam(value = "customer", required = false) Long customerId,
                           @RequestParam(value = "project", required = false) Long projectId,
                           @RequestParam(value = "from", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(value = "to", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           Model model) {
        model.addAttribute("billable", timeEntryService.readyToBill(customerId, projectId, from, to));
        model.addAttribute("customers", projectService.customersForPicker(customerId));
        model.addAttribute("projects", projectService.all());
        model.addAttribute("taxRates", taxRateRepository.findByActiveTrueOrderByCodeAsc());
        model.addAttribute("baseCurrency", projectService.baseCurrency());
        model.addAttribute("customerId", customerId);
        model.addAttribute("projectId", projectId);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("dueDefault", LocalDate.now().plusDays(30));
        return "projects/billable";
    }

    @PostMapping("/billable")
    public String bill(@ModelAttribute("form") TimeBillingForm form, RedirectAttributes ra) {
        try {
            TimeEntryService.BillingResult result =
                    timeEntryService.bill(form, AuditService.currentUsername());
            String detail = messageSource.getMessage("tsh.billedDetail",
                    new Object[]{result.entriesBilled(), result.invoiceNumbers()},
                    LocaleContextHolder.getLocale());
            ra.addFlashAttribute("flashMessage", flash("tsh.billed", detail));
            if (result.skipped() > 0) {
                ra.addFlashAttribute("skipped", result.skipped());
            }
            if (result.invoiceCount() == 1) {
                return "redirect:/invoices/" + result.invoices().get(0).getId();
            }
            return "redirect:/invoices";
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("tsh.actionFailed", ex.getMessage()));
            return "redirect:/projects/billable";
        }
    }
}
