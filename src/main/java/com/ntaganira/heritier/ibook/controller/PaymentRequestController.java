/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : PaymentRequestController.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : Payment request web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.PaymentRequestForm;
import com.ntaganira.heritier.ibook.entity.PaymentRequest;
import com.ntaganira.heritier.ibook.service.AuditService;
import com.ntaganira.heritier.ibook.service.PaymentRequestService;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serves {@code /sales/payment-links}.
 *
 * <p>The route keeps the name the sidebar links to, but the page is a register of payment
 * <em>requests</em>, not links. There is no gateway to issue a link, and the page says so rather than
 * producing a URL that somebody would forward to a customer.
 */
@Controller
@RequestMapping("/sales/payment-links")
public class PaymentRequestController {

    private final PaymentRequestService paymentRequestService;
    private final MessageSource messageSource;

    public PaymentRequestController(PaymentRequestService paymentRequestService,
                                    MessageSource messageSource) {
        this.paymentRequestService = paymentRequestService;
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

    private Map<String, String> channelLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        for (String channel : new String[]{"MTN_MOMO", "AIRTEL_MONEY", "BANK_TRANSFER", "CASH", "CARD"}) {
            labels.put(channel, msg("prq.channel." + channel.toLowerCase()));
        }
        return labels;
    }

    @GetMapping
    public String requests(@RequestParam(value = "from", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(value = "to", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           @RequestParam(value = "invoiceId", required = false) Long invoiceId,
                           Model model) {
        LocalDate today = LocalDate.now();
        LocalDate periodFrom = from == null ? today.withDayOfMonth(1).minusMonths(2) : from;
        LocalDate periodTo = to == null ? today : to;

        model.addAttribute("overview", paymentRequestService.overview(periodFrom, periodTo));
        model.addAttribute("channelLabels", channelLabels());
        model.addAttribute("from", periodFrom);
        model.addAttribute("to", periodTo);
        if (!model.containsAttribute("form")) {
            PaymentRequestForm empty = PaymentRequestForm.empty();
            model.addAttribute("form", invoiceId == null ? empty
                    : new PaymentRequestForm(invoiceId, null, empty.channel(),
                            paymentRequestService.suggestedPayTo(empty.channel()), "",
                            LocalDate.now(), ""));
        }
        return "sales/payment-links";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") PaymentRequestForm form,
                         BindingResult binding,
                         RedirectAttributes ra) {
        if (binding.hasErrors()) {
            ra.addFlashAttribute("flashMessage", errorFlash("prq.saveFailed", msg("prq.fixFields")));
            return "redirect:/sales/payment-links";
        }
        try {
            PaymentRequest saved = paymentRequestService.create(form, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage",
                    flash("prq.created", saved.getRequestNo() + " — " + saved.getInvoiceNo()));
        } catch (IllegalStateException | IllegalArgumentException e) {
            ra.addFlashAttribute("flashMessage", errorFlash("prq.saveFailed", e.getMessage()));
        }
        return "redirect:/sales/payment-links";
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes ra) {
        try {
            paymentRequestService.cancel(id);
            ra.addFlashAttribute("flashMessage", flash("prq.cancelled", null));
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flashMessage", errorFlash("prq.saveFailed", e.getMessage()));
        }
        return "redirect:/sales/payment-links";
    }
}
