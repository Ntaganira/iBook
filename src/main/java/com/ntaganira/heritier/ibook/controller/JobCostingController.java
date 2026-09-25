/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : JobCostingController.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Job costing and project profitability web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.enums.ProjectStatus;
import com.ntaganira.heritier.ibook.service.AuditService;
import com.ntaganira.heritier.ibook.service.JobCostingService;
import com.ntaganira.heritier.ibook.service.ProjectService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/projects")
public class JobCostingController {

    private static final int PAGE_SIZE = 25;

    private final JobCostingService jobCostingService;
    private final ProjectService projectService;
    private final MessageSource messageSource;

    public JobCostingController(JobCostingService jobCostingService,
                                ProjectService projectService,
                                MessageSource messageSource) {
        this.jobCostingService = jobCostingService;
        this.projectService = projectService;
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

    private Map<String, String> statusLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (ProjectStatus s : ProjectStatus.values()) {
            m.put(s.name(), msg("prj.status." + s.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private static LocalDate startOrDefault(LocalDate from) {
        return from == null ? LocalDate.now().withDayOfYear(1) : from;
    }

    private static LocalDate endOrDefault(LocalDate to) {
        return to == null ? LocalDate.now() : to;
    }

    // ----- Job costing -----

    @GetMapping("/job-costing")
    public String jobCosting(@RequestParam(value = "from", required = false) String fromText,
                             @RequestParam(value = "to", required = false) String toText,
                             @RequestParam(value = "account", required = false) Long accountId,
                             @RequestParam(value = "project", required = false) Long projectId,
                             @RequestParam(value = "untagged", defaultValue = "false") boolean untagged,
                             @RequestParam(value = "sort", defaultValue = "entry.entryDate") String sort,
                             @RequestParam(value = "dir", defaultValue = "desc") String dir,
                             @RequestParam(value = "page", defaultValue = "0") int page,
                             Model model) {
        LocalDate from = startOrDefault(parseDate(fromText));
        LocalDate to = endOrDefault(parseDate(toText));
        SortSpec sp = SortSpec.resolve(sort, dir, "entry.entryDate",
                "entry.entryDate", "accountCode", "projectCode", "debit", "credit");

        model.addAttribute("lines", jobCostingService.lines(from, to, accountId, projectId,
                untagged, PageRequest.of(page, PAGE_SIZE, sp.sort())));
        model.addAttribute("accounts", jobCostingService.costingAccounts());
        model.addAttribute("projects", jobCostingService.taggableProjects());
        model.addAttribute("coverage", jobCostingService.coverage(from, to));
        model.addAttribute("baseCurrency", projectService.baseCurrency());
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("accountId", accountId);
        model.addAttribute("projectId", projectId);
        model.addAttribute("untagged", untagged);

        StringBuilder fq = new StringBuilder("from=").append(from).append("&to=").append(to);
        if (accountId != null) {
            fq.append("&account=").append(accountId);
        }
        if (projectId != null) {
            fq.append("&project=").append(projectId);
        }
        if (untagged) {
            fq.append("&untagged=true");
        }
        SortSpec.addListContext(model, "/projects/job-costing", "?" + fq, sp);
        return "projects/job-costing";
    }

    @PostMapping("/job-costing/tag")
    public String tag(@RequestParam(value = "lineIds", required = false) List<Long> lineIds,
                      @RequestParam(value = "projectId", required = false) Long projectId,
                      @RequestParam(value = "back", required = false) String back,
                      RedirectAttributes ra) {
        try {
            JobCostingService.TagResult result =
                    jobCostingService.tag(lineIds, projectId, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("jc.tagged",
                    messageSource.getMessage("jc.taggedDetail",
                            new Object[]{result.tagged(), result.project().getCode(),
                                    result.refused()}, LocaleContextHolder.getLocale())));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("jc.actionFailed", ex.getMessage()));
        }
        return "redirect:" + backOrDefault(back);
    }

    @PostMapping("/job-costing/untag")
    public String untag(@RequestParam(value = "lineIds", required = false) List<Long> lineIds,
                        @RequestParam(value = "back", required = false) String back,
                        RedirectAttributes ra) {
        try {
            int cleared = jobCostingService.untag(lineIds, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("jc.untagged",
                    messageSource.getMessage("jc.untaggedDetail", new Object[]{cleared},
                            LocaleContextHolder.getLocale())));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("jc.actionFailed", ex.getMessage()));
        }
        return "redirect:" + backOrDefault(back);
    }

    /** Only a path back into this module is honoured, so the parameter cannot bounce elsewhere. */
    private static String backOrDefault(String back) {
        if (back != null && back.startsWith("/projects/job-costing")) {
            return back;
        }
        return "/projects/job-costing";
    }

    // ----- Profitability -----

    @GetMapping("/profitability")
    public String profitability(@RequestParam(value = "from", required = false) String fromText,
                                @RequestParam(value = "to", required = false) String toText,
                                @RequestParam(value = "status", required = false) String status,
                                Model model) {
        LocalDate from = startOrDefault(parseDate(fromText));
        LocalDate to = endOrDefault(parseDate(toText));
        model.addAttribute("report", jobCostingService.profitability(from, to, status));
        model.addAttribute("coverage", jobCostingService.coverage(from, to));
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("baseCurrency", projectService.baseCurrency());
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("status", status == null ? "" : status);
        return "projects/profitability";
    }

    private static LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
