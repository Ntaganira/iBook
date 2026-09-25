/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : RecurringJournalController.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Recurring journal schedule web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.RecurringJournalForm;
import com.ntaganira.heritier.ibook.entity.JournalEntry;
import com.ntaganira.heritier.ibook.entity.RecurringJournal;
import com.ntaganira.heritier.ibook.entity.RecurringJournalLine;
import com.ntaganira.heritier.ibook.enums.RecurrenceFrequency;
import com.ntaganira.heritier.ibook.enums.RecurringJournalStatus;
import com.ntaganira.heritier.ibook.service.AuditService;
import com.ntaganira.heritier.ibook.service.RecurringJournalService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/accounting/recurring-journals")
public class RecurringJournalController {

    private static final int PAGE_SIZE = 20;

    private final RecurringJournalService recurringJournalService;
    private final MessageSource messageSource;

    public RecurringJournalController(RecurringJournalService recurringJournalService,
                                      MessageSource messageSource) {
        this.recurringJournalService = recurringJournalService;
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
        String reason = ex.getMessage() == null ? msg("rjn.actionFailed") : ex.getMessage();
        br.reject("rjn.failedDetail", new Object[]{reason}, reason);
    }

    private Map<String, String> statusLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (RecurringJournalStatus s : RecurringJournalStatus.values()) {
            m.put(s.name(), msg("rjn.status." + s.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private Map<String, String> frequencyLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (RecurrenceFrequency f : RecurrenceFrequency.values()) {
            m.put(f.name(), msg("rjn.frequency." + f.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private void addFormContext(Model model, String mode, Long editingId) {
        model.addAttribute("mode", mode);
        model.addAttribute("editingId", editingId);
        model.addAttribute("accounts", recurringJournalService.postableAccounts());
        model.addAttribute("frequencyLabels", frequencyLabels());
    }

    // ----- List -----

    @GetMapping
    public String schedules(@RequestParam(value = "q", required = false) String q,
                            @RequestParam(value = "status", required = false) String status,
                            @RequestParam(value = "sort", defaultValue = "nextRunDate") String sort,
                            @RequestParam(value = "dir", defaultValue = "asc") String dir,
                            @RequestParam(value = "page", defaultValue = "0") int page,
                            Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "nextRunDate", "nextRunDate", "name",
                "frequency", "status", "totalDebits");
        model.addAttribute("schedules", recurringJournalService.list(q, status,
                PageRequest.of(page, PAGE_SIZE, sp.sort())));
        model.addAttribute("summary", recurringJournalService.summary());
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("frequencyLabels", frequencyLabels());
        model.addAttribute("q", q);
        model.addAttribute("status", status == null ? "" : status);

        StringBuilder fq = new StringBuilder();
        if (q != null && !q.isBlank()) {
            fq.append("q=").append(UriUtils.encodeQueryParam(q, StandardCharsets.UTF_8));
        }
        if (status != null && !status.isBlank()) {
            if (!fq.isEmpty()) {
                fq.append('&');
            }
            fq.append("status=").append(status);
        }
        SortSpec.addListContext(model, "/accounting/recurring-journals",
                fq.isEmpty() ? "" : "?" + fq, sp);
        return "journals/recurring";
    }

    // ----- Create / edit -----

    @GetMapping("/new")
    public String newSchedule(Model model) {
        addFormContext(model, "create", null);
        model.addAttribute("form", RecurringJournalForm.empty());
        return "journals/recurring-form";
    }

    @GetMapping("/{id}/edit")
    public String editSchedule(@PathVariable Long id, Model model, RedirectAttributes ra) {
        RecurringJournal schedule = recurringJournalService.get(id);
        if (schedule == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("rjn.notFound", null));
            return "redirect:/accounting/recurring-journals";
        }
        if (!schedule.isEditable()) {
            ra.addFlashAttribute("flashMessage", errorFlash("rjn.notEditable", schedule.getName()));
            return "redirect:/accounting/recurring-journals/" + id;
        }
        addFormContext(model, "edit", id);
        model.addAttribute("form", toForm(schedule));
        return "journals/recurring-form";
    }

    private RecurringJournalForm toForm(RecurringJournal schedule) {
        RecurringJournalForm form = new RecurringJournalForm();
        form.setName(schedule.getName());
        form.setDescription(schedule.getDescription());
        form.setFrequency(schedule.getFrequency().name());
        form.setStartDate(schedule.getStartDate());
        form.setEndDate(schedule.getEndDate());
        form.setMaxOccurrences(schedule.getMaxOccurrences());
        form.setAutoPost(schedule.isAutoPost());
        form.setReference(schedule.getReference());
        form.setMemo(schedule.getMemo());
        form.setNotes(schedule.getNotes());
        int index = 0;
        for (RecurringJournalLine line : schedule.getLines()) {
            RecurringJournalForm.Line row = form.getLines().get(index++);
            row.setAccountId(line.getAccountId());
            row.setMemo(line.getMemo());
            row.setDebit(line.getDebit());
            row.setCredit(line.getCredit());
        }
        for (int i = 0; i < 2; i++) {
            form.getLines().get(index++);
        }
        return form;
    }

    @PostMapping
    public String create(@ModelAttribute("form") RecurringJournalForm form, BindingResult br,
                         Model model, RedirectAttributes ra) {
        validate(form, br, null);
        if (br.hasErrors()) {
            addFormContext(model, "create", null);
            return "journals/recurring-form";
        }
        try {
            RecurringJournal saved = recurringJournalService.save(form, null,
                    AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("rjn.saved", saved.getName()));
            return "redirect:/accounting/recurring-journals/" + saved.getId();
        } catch (RuntimeException ex) {
            rejectWithReason(br, ex);
            addFormContext(model, "create", null);
            return "journals/recurring-form";
        }
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute("form") RecurringJournalForm form,
                         BindingResult br, Model model, RedirectAttributes ra) {
        if (recurringJournalService.get(id) == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("rjn.notFound", null));
            return "redirect:/accounting/recurring-journals";
        }
        validate(form, br, id);
        if (br.hasErrors()) {
            addFormContext(model, "edit", id);
            return "journals/recurring-form";
        }
        try {
            RecurringJournal saved = recurringJournalService.save(form, id,
                    AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("rjn.saved", saved.getName()));
            return "redirect:/accounting/recurring-journals/" + saved.getId();
        } catch (RuntimeException ex) {
            rejectWithReason(br, ex);
            addFormContext(model, "edit", id);
            return "journals/recurring-form";
        }
    }

    private void validate(RecurringJournalForm form, BindingResult br, Long excludeId) {
        if (form.getName() == null || form.getName().isBlank()) {
            br.rejectValue("name", "rjn.nameRequired");
        } else if (recurringJournalService.nameExists(form.getName(), excludeId)) {
            br.rejectValue("name", "rjn.nameExists");
        }
        if (form.getStartDate() == null) {
            br.rejectValue("startDate", "rjn.startRequired");
        }
        if (!form.balanced()) {
            br.reject("rjn.outOfBalance", new Object[]{
                    form.totalDebits().toPlainString(), form.totalCredits().toPlainString()},
                    "Debits and credits do not agree");
        }
    }

    // ----- View -----

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model, RedirectAttributes ra) {
        RecurringJournal schedule = recurringJournalService.get(id);
        if (schedule == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("rjn.notFound", null));
            return "redirect:/accounting/recurring-journals";
        }
        model.addAttribute("schedule", schedule);
        model.addAttribute("entries", recurringJournalService.entriesFrom(schedule));
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("frequencyLabels", frequencyLabels());
        return "journals/recurring-view";
    }

    // ----- Lifecycle -----

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id, RedirectAttributes ra) {
        return lifecycle(id, ra, "rjn.activated", () -> recurringJournalService.activate(id));
    }

    @PostMapping("/{id}/pause")
    public String pause(@PathVariable Long id, RedirectAttributes ra) {
        return lifecycle(id, ra, "rjn.paused", () -> recurringJournalService.pause(id));
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id,
                         @RequestParam(value = "reason", required = false) String reason,
                         RedirectAttributes ra) {
        return lifecycle(id, ra, "rjn.cancelled", () -> recurringJournalService.cancel(id, reason));
    }

    @PostMapping("/{id}/run")
    public String runNow(@PathVariable Long id, RedirectAttributes ra) {
        try {
            JournalEntry entry = recurringJournalService.runNow(id, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("rjn.generated",
                    messageSource.getMessage("rjn.generatedDetail",
                            new Object[]{entry.getEntryNo(),
                                    msg("rjn.state." + entry.getStatus().name().toLowerCase(Locale.ROOT))},
                            LocaleContextHolder.getLocale())));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("rjn.actionFailed", ex.getMessage()));
        }
        return "redirect:/accounting/recurring-journals/" + id;
    }

    private String lifecycle(Long id, RedirectAttributes ra, String successKey,
                             java.util.function.Supplier<RecurringJournal> action) {
        try {
            action.get();
            ra.addFlashAttribute("flashMessage", flash(successKey, null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("rjn.actionFailed", ex.getMessage()));
        }
        return "redirect:/accounting/recurring-journals/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            recurringJournalService.delete(id);
            ra.addFlashAttribute("flashMessage", flash("rjn.deleted", null));
            return "redirect:/accounting/recurring-journals";
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("rjn.actionFailed", ex.getMessage()));
            return "redirect:/accounting/recurring-journals/" + id;
        }
    }
}
