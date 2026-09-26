/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : ExciseController.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : Excise duty web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.ExciseDutyForm;
import com.ntaganira.heritier.ibook.entity.ExciseDuty;
import com.ntaganira.heritier.ibook.enums.ExciseBasis;
import com.ntaganira.heritier.ibook.service.AuditService;
import com.ntaganira.heritier.ibook.service.ExciseService;
import jakarta.validation.Valid;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/taxes/excise")
public class ExciseController {

    private static final int PAGE_SIZE = 20;

    private final ExciseService exciseService;
    private final MessageSource messageSource;

    public ExciseController(ExciseService exciseService, MessageSource messageSource) {
        this.exciseService = exciseService;
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

    private Map<String, String> basisLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (ExciseBasis b : ExciseBasis.values()) {
            m.put(b.name(), msg("exc.basis." + b.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    // ----- List -----

    @GetMapping
    public String duties(@RequestParam(value = "q", required = false) String q,
                         @RequestParam(value = "active", required = false) String active,
                         @RequestParam(value = "from", required = false)
                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam(value = "to", required = false)
                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                         @RequestParam(value = "sort", defaultValue = "code") String sort,
                         @RequestParam(value = "dir", defaultValue = "asc") String dir,
                         @RequestParam(value = "page", defaultValue = "0") int page,
                         Model model) {
        LocalDate today = LocalDate.now();
        LocalDate periodFrom = from == null ? today.withDayOfMonth(1) : from;
        LocalDate periodTo = to == null ? today.withDayOfMonth(today.lengthOfMonth()) : to;

        SortSpec sp = SortSpec.resolve(sort, dir, "code", "code", "name", "rate", "confirmed");
        model.addAttribute("duties", exciseService.list(q, active,
                PageRequest.of(page, PAGE_SIZE, sp.sort())));
        model.addAttribute("summary", exciseService.summary(periodFrom, periodTo));
        model.addAttribute("basisLabels", basisLabels());
        model.addAttribute("activeDuties", exciseService.active());
        model.addAttribute("paymentAccounts", exciseService.paymentAccounts());
        model.addAttribute("missingAccounts", exciseService.missingExpectedAccounts());
        model.addAttribute("q", q);
        model.addAttribute("active", active == null ? "" : active);
        model.addAttribute("from", periodFrom);
        model.addAttribute("to", periodTo);
        model.addAttribute("today", today);

        StringBuilder fq = new StringBuilder();
        fq.append("from=").append(periodFrom).append("&to=").append(periodTo);
        if (q != null && !q.isBlank()) {
            fq.append("&q=").append(UriUtils.encodeQueryParam(q, StandardCharsets.UTF_8));
        }
        if (active != null && !active.isBlank()) {
            fq.append("&active=").append(active);
        }
        SortSpec.addListContext(model, "/taxes/excise", "?" + fq, sp);
        return "taxes/excise";
    }

    // ----- Form -----

    @GetMapping("/new")
    public String newDuty(Model model) {
        model.addAttribute("mode", "new");
        model.addAttribute("editingId", null);
        model.addAttribute("accounts", exciseService.liabilityAccounts());
        model.addAttribute("basisLabels", basisLabels());
        model.addAttribute("form", ExciseDutyForm.empty());
        return "taxes/excise-form";
    }

    @GetMapping("/{id}/edit")
    public String editDuty(@PathVariable Long id, Model model, RedirectAttributes ra) {
        ExciseDuty duty = exciseService.get(id);
        if (duty == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("exc.notFound", null));
            return "redirect:/taxes/excise";
        }
        model.addAttribute("mode", "edit");
        model.addAttribute("editingId", id);
        model.addAttribute("accounts", exciseService.liabilityAccounts());
        model.addAttribute("basisLabels", basisLabels());
        model.addAttribute("duty", duty);
        model.addAttribute("productCount", exciseService.productCount(id));
        model.addAttribute("products", exciseService.productsFor(id));
        model.addAttribute("form", exciseService.toForm(duty));
        return "taxes/excise-form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") ExciseDutyForm form, BindingResult br,
                         Model model, RedirectAttributes ra) {
        if (!br.hasErrors()) {
            try {
                ExciseDuty saved = exciseService.save(form, null, AuditService.currentUsername());
                ra.addFlashAttribute("flashMessage", flash("exc.saved", saved.getName()));
                return "redirect:/taxes/excise/" + saved.getId() + "/edit";
            } catch (RuntimeException ex) {
                String reason = ex.getMessage() == null ? msg("exc.actionFailed") : ex.getMessage();
                br.reject("exc.failedDetail", new Object[]{reason}, reason);
            }
        }
        model.addAttribute("mode", "new");
        model.addAttribute("editingId", null);
        model.addAttribute("accounts", exciseService.liabilityAccounts());
        model.addAttribute("basisLabels", basisLabels());
        return "taxes/excise-form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") ExciseDutyForm form,
                         BindingResult br, Model model, RedirectAttributes ra) {
        if (exciseService.get(id) == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("exc.notFound", null));
            return "redirect:/taxes/excise";
        }
        if (!br.hasErrors()) {
            try {
                ExciseDuty saved = exciseService.save(form, id, AuditService.currentUsername());
                ra.addFlashAttribute("flashMessage", flash("exc.saved", saved.getName()));
                return "redirect:/taxes/excise/" + saved.getId() + "/edit";
            } catch (RuntimeException ex) {
                String reason = ex.getMessage() == null ? msg("exc.actionFailed") : ex.getMessage();
                br.reject("exc.failedDetail", new Object[]{reason}, reason);
            }
        }
        model.addAttribute("mode", "edit");
        model.addAttribute("editingId", id);
        model.addAttribute("accounts", exciseService.liabilityAccounts());
        model.addAttribute("basisLabels", basisLabels());
        model.addAttribute("duty", exciseService.get(id));
        model.addAttribute("productCount", exciseService.productCount(id));
        model.addAttribute("products", exciseService.productsFor(id));
        return "taxes/excise-form";
    }

    // ----- Lifecycle -----

    @PostMapping("/{id}/confirm")
    public String confirm(@PathVariable Long id,
                          @RequestParam(value = "rateSource", required = false) String rateSource,
                          RedirectAttributes ra) {
        try {
            exciseService.confirm(id, rateSource, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("exc.confirmed", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("exc.actionFailed", ex.getMessage()));
        }
        return "redirect:/taxes/excise/" + id + "/edit";
    }

    @PostMapping("/{id}/withdraw")
    public String withdraw(@PathVariable Long id, RedirectAttributes ra) {
        try {
            exciseService.withdraw(id, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("exc.withdrawn", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("exc.actionFailed", ex.getMessage()));
        }
        return "redirect:/taxes/excise/" + id + "/edit";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            exciseService.delete(id);
            ra.addFlashAttribute("flashMessage", flash("exc.deleted", null));
            return "redirect:/taxes/excise";
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("exc.actionFailed", ex.getMessage()));
            return "redirect:/taxes/excise/" + id + "/edit";
        }
    }

    @PostMapping("/assign")
    public String assign(@RequestParam("productId") Long productId,
                         @RequestParam(value = "dutyId", required = false) Long dutyId,
                         @RequestParam(value = "back", required = false) String back,
                         RedirectAttributes ra) {
        try {
            exciseService.assignToProduct(productId, dutyId);
            ra.addFlashAttribute("flashMessage",
                    flash(dutyId == null ? "exc.unassigned" : "exc.assigned", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("exc.actionFailed", ex.getMessage()));
        }
        // Only a path inside this module is honoured, so a crafted parameter cannot bounce
        // somebody somewhere else.
        return "redirect:" + (back != null && back.startsWith("/taxes/excise")
                ? back : "/taxes/excise");
    }

    @PostMapping("/remit")
    public String remit(@RequestParam("dutyId") Long dutyId,
                        @RequestParam("paymentDate")
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paymentDate,
                        @RequestParam("amount") BigDecimal amount,
                        @RequestParam("paymentAccountId") Long paymentAccountId,
                        @RequestParam(value = "declarationNo", required = false) String declarationNo,
                        RedirectAttributes ra) {
        try {
            exciseService.remit(dutyId, paymentDate, amount, paymentAccountId, declarationNo,
                    AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("exc.remitted", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("exc.actionFailed", ex.getMessage()));
        }
        return "redirect:/taxes/excise";
    }
}
