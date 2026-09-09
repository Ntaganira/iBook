/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : PageController.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : Thymeleaf view routing controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping({"/", "/dashboard"})
    public String dashboard() {
        return "dashboard/index";
    }

    @GetMapping("/customers")
    public String customers() {
        return "customers/list";
    }

    @GetMapping("/customers/new")
    public String newCustomer() {
        return "customers/form";
    }

    @GetMapping("/customers/{id}/edit")
    public String editCustomer() {
        return "customers/form";
    }

    @GetMapping("/invoices")
    public String invoices() {
        return "invoices/list";
    }

    @GetMapping("/invoices/new")
    public String newInvoice() {
        return "invoices/form";
    }

    @GetMapping("/invoices/{id}")
    public String viewInvoice() {
        return "invoices/view";
    }

    @GetMapping({"/banking", "/banking/transactions"})
    public String bankingTransactions() {
        return "banking/transactions";
    }

    @GetMapping("/accounts")
    public String chartOfAccounts() {
        return "accounts/list";
    }

    @GetMapping("/journals/new")
    public String newJournalEntry() {
        return "journals/form";
    }

    @GetMapping("/reports")
    public String reports() {
        return "reports/index";
    }

    @GetMapping("/error/404")
    public String notFound() {
        return "error/404";
    }

    @GetMapping("/error/403")
    public String forbidden() {
        return "error/403";
    }
}