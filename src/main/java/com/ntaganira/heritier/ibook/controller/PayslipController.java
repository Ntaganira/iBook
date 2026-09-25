/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : PayslipController.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Payslip web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.enums.PayrollRunStatus;
import com.ntaganira.heritier.ibook.service.PayslipService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/payroll/payslips")
public class PayslipController {

    private static final int PAGE_SIZE = 25;

    private final PayslipService payslipService;
    private final MessageSource messageSource;

    public PayslipController(PayslipService payslipService, MessageSource messageSource) {
        this.payslipService = payslipService;
        this.messageSource = messageSource;
    }

    private String msg(String key) {
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
    }

    private Map<String, String> statusLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (PayrollRunStatus s : PayrollRunStatus.values()) {
            m.put(s.name(), msg("pry.status." + s.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    @GetMapping
    public String payslips(@RequestParam(value = "q", required = false) String q,
                           @RequestParam(value = "employeeId", required = false) Long employeeId,
                           @RequestParam(value = "status", required = false) String status,
                           @RequestParam(value = "from", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(value = "to", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           @RequestParam(value = "sort", defaultValue = "employeeName") String sort,
                           @RequestParam(value = "dir", defaultValue = "asc") String dir,
                           @RequestParam(value = "page", defaultValue = "0") int page,
                           Model model) {
        LocalDate today = LocalDate.now();
        LocalDate periodFrom = from == null ? today.withDayOfYear(1) : from;
        LocalDate periodTo = to == null ? today : to;

        SortSpec sp = SortSpec.resolve(sort, dir, "employeeName", "employeeName", "employeeNo",
                "grossPay", "netPay", "paye");
        model.addAttribute("payslips", payslipService.list(q, employeeId, status,
                periodFrom, periodTo, PageRequest.of(page, PAGE_SIZE, sp.sort())));
        model.addAttribute("totals", payslipService.totals(periodFrom, periodTo));
        model.addAttribute("employees", payslipService.employees());
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("q", q);
        model.addAttribute("employeeId", employeeId);
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("from", periodFrom);
        model.addAttribute("to", periodTo);

        StringBuilder fq = new StringBuilder();
        fq.append("from=").append(periodFrom).append("&to=").append(periodTo);
        if (q != null && !q.isBlank()) {
            fq.append("&q=").append(UriUtils.encodeQueryParam(q, StandardCharsets.UTF_8));
        }
        if (employeeId != null) {
            fq.append("&employeeId=").append(employeeId);
        }
        if (status != null && !status.isBlank()) {
            fq.append("&status=").append(status);
        }
        SortSpec.addListContext(model, "/payroll/payslips", "?" + fq, sp);
        return "payroll/payslips";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model, RedirectAttributes ra) {
        PayslipService.Detail detail = payslipService.detail(id);
        if (detail == null) {
            ra.addFlashAttribute("flashMessage", Map.of("title", msg("psl.notFound"),
                    "detail", "", "type", "error"));
            return "redirect:/payroll/payslips";
        }
        model.addAttribute("payslip", detail.payslip());
        model.addAttribute("runId", detail.runId());
        model.addAttribute("runStatus", detail.runStatus());
        model.addAttribute("rateNote", detail.rateNote());
        model.addAttribute("statusLabels", statusLabels());
        return "payroll/payslip-view";
    }
}
