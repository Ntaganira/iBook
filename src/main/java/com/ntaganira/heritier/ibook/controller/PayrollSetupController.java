/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : PayrollSetupController.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Payroll rates and accounts web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.PayrollSettingsForm;
import com.ntaganira.heritier.ibook.entity.PayrollSettings;
import com.ntaganira.heritier.ibook.service.AuditService;
import com.ntaganira.heritier.ibook.service.PayrollSettingsService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/payroll/setup")
public class PayrollSetupController {

    private final PayrollSettingsService payrollSettingsService;
    private final MessageSource messageSource;

    public PayrollSetupController(PayrollSettingsService payrollSettingsService,
                                  MessageSource messageSource) {
        this.payrollSettingsService = payrollSettingsService;
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

    private void addContext(Model model, PayrollSettings settings) {
        model.addAttribute("settings", settings);
        model.addAttribute("accounts", payrollSettingsService.accounts());
        model.addAttribute("missingAccounts", payrollSettingsService.missingExpectedAccounts());
    }

    @GetMapping
    public String setup(Model model) {
        PayrollSettings settings = payrollSettingsService.current();
        addContext(model, settings);
        model.addAttribute("form", payrollSettingsService.toForm(settings));
        return "payroll/setup";
    }

    @PostMapping
    public String save(@ModelAttribute("form") PayrollSettingsForm form, BindingResult br,
                       Model model, RedirectAttributes ra) {
        try {
            payrollSettingsService.save(form);
            ra.addFlashAttribute("flashMessage", flash("pst.saved", msg("pst.confirmWithdrawn")));
            return "redirect:/payroll/setup";
        } catch (RuntimeException ex) {
            String reason = ex.getMessage() == null ? msg("pst.actionFailed") : ex.getMessage();
            br.reject("pst.failedDetail", new Object[]{reason}, reason);
            addContext(model, payrollSettingsService.current());
            return "payroll/setup";
        }
    }

    @PostMapping("/confirm")
    public String confirm(@RequestParam(value = "rateSource", required = false) String rateSource,
                          RedirectAttributes ra) {
        try {
            payrollSettingsService.confirm(rateSource, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("pst.confirmed", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("pst.actionFailed", ex.getMessage()));
        }
        return "redirect:/payroll/setup";
    }

    @PostMapping("/withdraw")
    public String withdraw(RedirectAttributes ra) {
        try {
            payrollSettingsService.withdrawConfirmation(AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("pst.withdrawn", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("pst.actionFailed", ex.getMessage()));
        }
        return "redirect:/payroll/setup";
    }
}
