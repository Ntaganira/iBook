/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : EmployeeController.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Employee register web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.EmployeeForm;
import com.ntaganira.heritier.ibook.entity.Employee;
import com.ntaganira.heritier.ibook.enums.EmployeeStatus;
import com.ntaganira.heritier.ibook.enums.PaymentMethod;
import com.ntaganira.heritier.ibook.service.EmployeeService;
import com.ntaganira.heritier.ibook.service.PayslipService;
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
@RequestMapping("/payroll/employees")
public class EmployeeController {

    private static final int PAGE_SIZE = 20;

    private final EmployeeService employeeService;
    private final PayslipService payslipService;
    private final MessageSource messageSource;

    public EmployeeController(EmployeeService employeeService,
                              PayslipService payslipService,
                              MessageSource messageSource) {
        this.employeeService = employeeService;
        this.payslipService = payslipService;
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
        String reason = ex.getMessage() == null ? msg("emp.actionFailed") : ex.getMessage();
        br.reject("emp.failedDetail", new Object[]{reason}, reason);
    }

    private Map<String, String> statusLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (EmployeeStatus s : EmployeeStatus.values()) {
            m.put(s.name(), msg("emp.status." + s.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private Map<String, String> methodLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (PaymentMethod method : PaymentMethod.values()) {
            m.put(method.name(), msg("emp.method." + method.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private void addFormContext(Model model, String mode, Long editingId) {
        model.addAttribute("mode", mode);
        model.addAttribute("editingId", editingId);
        model.addAttribute("components", employeeService.components());
        model.addAttribute("departments", employeeService.departments());
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("methodLabels", methodLabels());
    }

    // ----- List -----

    @GetMapping
    public String employees(@RequestParam(value = "q", required = false) String q,
                            @RequestParam(value = "status", required = false) String status,
                            @RequestParam(value = "department", required = false) String department,
                            @RequestParam(value = "sort", defaultValue = "lastName") String sort,
                            @RequestParam(value = "dir", defaultValue = "asc") String dir,
                            @RequestParam(value = "page", defaultValue = "0") int page,
                            Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "lastName", "lastName", "firstName",
                "employeeNo", "department", "basicSalary", "hireDate", "status");
        model.addAttribute("employees", employeeService.list(q, status, department,
                PageRequest.of(page, PAGE_SIZE, sp.sort())));
        model.addAttribute("summary", employeeService.summary());
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("departments", employeeService.departments());
        model.addAttribute("q", q);
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("department", department == null ? "" : department);

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
        if (department != null && !department.isBlank()) {
            if (!fq.isEmpty()) {
                fq.append('&');
            }
            fq.append("department=").append(UriUtils.encodeQueryParam(department, StandardCharsets.UTF_8));
        }
        SortSpec.addListContext(model, "/payroll/employees",
                fq.isEmpty() ? "" : "?" + fq, sp);
        return "payroll/employees";
    }

    // ----- Form -----

    @GetMapping("/new")
    public String newEmployee(Model model) {
        addFormContext(model, "new", null);
        model.addAttribute("form", EmployeeForm.empty());
        return "payroll/employee-form";
    }

    @GetMapping("/{id}/edit")
    public String editEmployee(@PathVariable Long id, Model model, RedirectAttributes ra) {
        Employee employee = employeeService.get(id);
        if (employee == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("emp.notFound", null));
            return "redirect:/payroll/employees";
        }
        addFormContext(model, "edit", id);
        model.addAttribute("form", employeeService.toForm(employee));
        return "payroll/employee-form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") EmployeeForm form, BindingResult br,
                         Model model, RedirectAttributes ra) {
        if (!br.hasErrors()) {
            try {
                Employee saved = employeeService.save(form, null);
                ra.addFlashAttribute("flashMessage", flash("emp.saved", saved.getFullName()));
                return "redirect:/payroll/employees/" + saved.getId();
            } catch (RuntimeException ex) {
                rejectWithReason(br, ex);
            }
        }
        addFormContext(model, "new", null);
        return "payroll/employee-form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") EmployeeForm form,
                         BindingResult br, Model model, RedirectAttributes ra) {
        if (employeeService.get(id) == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("emp.notFound", null));
            return "redirect:/payroll/employees";
        }
        if (!br.hasErrors()) {
            try {
                Employee saved = employeeService.save(form, id);
                ra.addFlashAttribute("flashMessage", flash("emp.saved", saved.getFullName()));
                return "redirect:/payroll/employees/" + saved.getId();
            } catch (RuntimeException ex) {
                rejectWithReason(br, ex);
            }
        }
        addFormContext(model, "edit", id);
        return "payroll/employee-form";
    }

    // ----- Detail -----

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model, RedirectAttributes ra) {
        Employee employee = employeeService.get(id);
        if (employee == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("emp.notFound", null));
            return "redirect:/payroll/employees";
        }
        model.addAttribute("employee", employee);
        model.addAttribute("gross", employeeService.grossFor(employee));
        model.addAttribute("payslips", payslipService.forEmployee(id));
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("methodLabels", methodLabels());
        return "payroll/employee-view";
    }

    // ----- Lifecycle -----

    @PostMapping("/{id}/status")
    public String setStatus(@PathVariable Long id,
                            @RequestParam("status") String status,
                            RedirectAttributes ra) {
        try {
            Employee saved = employeeService.setStatus(id, status);
            ra.addFlashAttribute("flashMessage", flash("emp.statusChanged", saved.getFullName()));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("emp.actionFailed", ex.getMessage()));
        }
        return "redirect:/payroll/employees/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            employeeService.delete(id);
            ra.addFlashAttribute("flashMessage", flash("emp.deleted", null));
            return "redirect:/payroll/employees";
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("emp.actionFailed", ex.getMessage()));
            return "redirect:/payroll/employees/" + id;
        }
    }
}
