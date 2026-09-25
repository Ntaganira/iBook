/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : PayrollRunController.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Payroll run web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.PayrollRunForm;
import com.ntaganira.heritier.ibook.entity.PayrollRun;
import com.ntaganira.heritier.ibook.entity.PayrollSettings;
import com.ntaganira.heritier.ibook.enums.PayrollRunStatus;
import com.ntaganira.heritier.ibook.service.AuditService;
import com.ntaganira.heritier.ibook.service.EmployeeService;
import com.ntaganira.heritier.ibook.service.PayrollRunService;
import com.ntaganira.heritier.ibook.service.PayrollSettingsService;
import jakarta.validation.Valid;
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
@RequestMapping("/payroll/runs")
public class PayrollRunController {

    private static final int PAGE_SIZE = 20;

    private final PayrollRunService payrollRunService;
    private final PayrollSettingsService payrollSettingsService;
    private final EmployeeService employeeService;
    private final MessageSource messageSource;

    public PayrollRunController(PayrollRunService payrollRunService,
                                PayrollSettingsService payrollSettingsService,
                                EmployeeService employeeService,
                                MessageSource messageSource) {
        this.payrollRunService = payrollRunService;
        this.payrollSettingsService = payrollSettingsService;
        this.employeeService = employeeService;
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
        String reason = ex.getMessage() == null ? msg("pry.actionFailed") : ex.getMessage();
        br.reject("pry.failedDetail", new Object[]{reason}, reason);
    }

    private Map<String, String> statusLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (PayrollRunStatus s : PayrollRunStatus.values()) {
            m.put(s.name(), msg("pry.status." + s.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private void addFormContext(Model model, String mode, Long editingId) {
        model.addAttribute("mode", mode);
        model.addAttribute("editingId", editingId);
        model.addAttribute("people", employeeService.runnable());
        model.addAttribute("departments", employeeService.departments());
        addSettingsContext(model);
    }

    /** Every page in this module says whether a run could post, because most of the time it cannot. */
    private void addSettingsContext(Model model) {
        PayrollSettings settings = payrollSettingsService.current();
        model.addAttribute("settings", settings);
        model.addAttribute("ratesConfirmed", settings.isConfirmed());
        model.addAttribute("accountsComplete", settings.isAccountsComplete());
    }

    // ----- List -----

    @GetMapping
    public String runs(@RequestParam(value = "q", required = false) String q,
                       @RequestParam(value = "status", required = false) String status,
                       @RequestParam(value = "sort", defaultValue = "payDate") String sort,
                       @RequestParam(value = "dir", defaultValue = "desc") String dir,
                       @RequestParam(value = "page", defaultValue = "0") int page,
                       Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "payDate", "payDate", "runNo", "periodStart",
                "totalGross", "totalNetPay", "status");
        model.addAttribute("runs", payrollRunService.list(q, status,
                PageRequest.of(page, PAGE_SIZE, sp.sort())));
        model.addAttribute("summary", payrollRunService.summary());
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("q", q);
        model.addAttribute("status", status == null ? "" : status);
        addSettingsContext(model);

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
        SortSpec.addListContext(model, "/payroll/runs", fq.isEmpty() ? "" : "?" + fq, sp);
        return "payroll/runs";
    }

    // ----- Form -----

    @GetMapping("/new")
    public String newRun(Model model) {
        addFormContext(model, "new", null);
        model.addAttribute("form", PayrollRunForm.empty());
        return "payroll/run-form";
    }

    @GetMapping("/{id}/edit")
    public String editRun(@PathVariable Long id, Model model, RedirectAttributes ra) {
        PayrollRun run = payrollRunService.get(id);
        if (run == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("pry.notFound", null));
            return "redirect:/payroll/runs";
        }
        if (!run.isEditable()) {
            ra.addFlashAttribute("flashMessage", errorFlash("pry.notEditable", run.getRunNo()));
            return "redirect:/payroll/runs/" + id;
        }
        addFormContext(model, "edit", id);
        model.addAttribute("form", new PayrollRunForm(run.getName(), run.getPeriodStart(),
                run.getPeriodEnd(), run.getPayDate(), null, run.getNotes(),
                run.getPayslips().stream().map(p -> p.getEmployeeId()).toList()));
        return "payroll/run-form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") PayrollRunForm form, BindingResult br,
                         Model model, RedirectAttributes ra) {
        if (!br.hasErrors()) {
            try {
                PayrollRun saved = payrollRunService.save(form, null, AuditService.currentUsername());
                ra.addFlashAttribute("flashMessage", flash("pry.calculated", saved.getRunNo()));
                return "redirect:/payroll/runs/" + saved.getId();
            } catch (RuntimeException ex) {
                rejectWithReason(br, ex);
            }
        }
        addFormContext(model, "new", null);
        return "payroll/run-form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") PayrollRunForm form,
                         BindingResult br, Model model, RedirectAttributes ra) {
        if (payrollRunService.get(id) == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("pry.notFound", null));
            return "redirect:/payroll/runs";
        }
        if (!br.hasErrors()) {
            try {
                PayrollRun saved = payrollRunService.save(form, id, AuditService.currentUsername());
                ra.addFlashAttribute("flashMessage", flash("pry.calculated", saved.getRunNo()));
                return "redirect:/payroll/runs/" + saved.getId();
            } catch (RuntimeException ex) {
                rejectWithReason(br, ex);
            }
        }
        addFormContext(model, "edit", id);
        return "payroll/run-form";
    }

    // ----- Detail -----

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model, RedirectAttributes ra) {
        PayrollRun run = payrollRunService.get(id);
        if (run == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("pry.notFound", null));
            return "redirect:/payroll/runs";
        }
        model.addAttribute("run", run);
        model.addAttribute("payslips", payrollRunService.payslipsOf(id));
        model.addAttribute("journal", payrollRunService.journalFor(run));
        model.addAttribute("statusLabels", statusLabels());
        addSettingsContext(model);
        return "payroll/run-view";
    }

    // ----- Lifecycle -----

    @PostMapping("/{id}/post")
    public String post(@PathVariable Long id, RedirectAttributes ra) {
        try {
            PayrollRun saved = payrollRunService.post(id, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("pry.posted", saved.getRunNo()));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("pry.actionFailed", ex.getMessage()));
        }
        return "redirect:/payroll/runs/" + id;
    }

    @PostMapping("/{id}/void")
    public String voidRun(@PathVariable Long id,
                          @RequestParam(value = "reason", required = false) String reason,
                          RedirectAttributes ra) {
        try {
            PayrollRun saved = payrollRunService.voidRun(id, reason, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("pry.voided", saved.getRunNo()));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("pry.actionFailed", ex.getMessage()));
        }
        return "redirect:/payroll/runs/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            payrollRunService.delete(id);
            ra.addFlashAttribute("flashMessage", flash("pry.deleted", null));
            return "redirect:/payroll/runs";
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("pry.actionFailed", ex.getMessage()));
            return "redirect:/payroll/runs/" + id;
        }
    }
}
