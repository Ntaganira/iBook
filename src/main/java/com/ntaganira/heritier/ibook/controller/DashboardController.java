/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : DashboardController.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : Dashboard overview controller (KPIs, charts, alerts)
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.security.Principal;
import com.ntaganira.heritier.ibook.service.DashboardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping({"/", "/dashboard"})
    public String dashboard(@AuthenticationPrincipal Principal principal, Model model) {
        String username = principal == null ? null : principal.getUsername();
        DashboardService.Snapshot data = dashboardService.snapshot(username);
        model.addAttribute("dash", data);
        model.addAttribute("company", data.company());
        return "dashboard/index";
    }
}