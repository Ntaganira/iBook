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

import com.ntaganira.heritier.ibook.entity.Customer;
import com.ntaganira.heritier.ibook.service.CustomerService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

@Controller
public class PageController {

    private static final int PAGE_SIZE = 25;

    private final CustomerService customerService;

    public PageController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping("/customers")
    public String customers(@RequestParam(value = "q", required = false) String q,
                            @RequestParam(value = "type", required = false) String type,
                            @RequestParam(value = "sort", defaultValue = "name") String sort,
                            @RequestParam(value = "dir", defaultValue = "asc") String dir,
                            @RequestParam(value = "page", defaultValue = "0") int page,
                            Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "name", "name", "companyName", "phone", "email", "city", "openingBalance", "active");
        Page<Customer> result = customerService.listCustomers(q, type, PageRequest.of(page, PAGE_SIZE, sp.sort()));
        model.addAttribute("customers", result);
        model.addAttribute("q", q);
        model.addAttribute("type", type == null ? "" : type);
        StringBuilder fq = new StringBuilder();
        if (q != null && !q.isBlank()) {
            fq.append("q=").append(UriUtils.encodeQueryParam(q, StandardCharsets.UTF_8));
        }
        if (type != null && !type.isBlank()) {
            if (!fq.isEmpty()) {
                fq.append('&');
            }
            fq.append("type=").append(type);
        }
        SortSpec.addListContext(model, "/customers", fq.isEmpty() ? "" : "?" + fq, sp);
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