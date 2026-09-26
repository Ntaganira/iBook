/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : EbmReadinessController.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : EBM readiness web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.service.EbmReadinessService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

/**
 * Serves {@code /sales/ebm}.
 *
 * <p>Read-only by design. There is no button to fiscalise, because there is nothing behind one.
 */
@Controller
@RequestMapping("/sales/ebm")
public class EbmReadinessController {

    private final EbmReadinessService ebmReadinessService;

    public EbmReadinessController(EbmReadinessService ebmReadinessService) {
        this.ebmReadinessService = ebmReadinessService;
    }

    @GetMapping
    public String readiness(@RequestParam(value = "from", required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(value = "to", required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            Model model) {
        LocalDate today = LocalDate.now();
        LocalDate periodFrom = from == null ? today.withDayOfMonth(1).minusMonths(11) : from;
        LocalDate periodTo = to == null ? today : to;

        model.addAttribute("review", ebmReadinessService.review(periodFrom, periodTo));
        model.addAttribute("from", periodFrom);
        model.addAttribute("to", periodTo);
        return "sales/ebm";
    }
}
