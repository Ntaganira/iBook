/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : ExpenseController.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Direct expenses web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.ExpenseForm;
import com.ntaganira.heritier.ibook.entity.Account;
import com.ntaganira.heritier.ibook.entity.Expense;
import com.ntaganira.heritier.ibook.entity.ExpenseLine;
import com.ntaganira.heritier.ibook.enums.AccountType;
import com.ntaganira.heritier.ibook.enums.ExpenseStatus;
import com.ntaganira.heritier.ibook.enums.PaymentMethod;
import com.ntaganira.heritier.ibook.service.*;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/purchases/expenses")
public class ExpenseController {

    private static final int PAGE_SIZE = 20;

    private final ExpenseService expenseService;
    private final VendorService vendorService;
    private final AccountingService accountingService;
    private final TaxRateService taxRateService;
    private final MessageSource messageSource;

    public ExpenseController(ExpenseService expenseService,
                             VendorService vendorService,
                             AccountingService accountingService,
                             TaxRateService taxRateService,
                             MessageSource messageSource) {
        this.expenseService = expenseService;
        this.vendorService = vendorService;
        this.accountingService = accountingService;
        this.taxRateService = taxRateService;
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

    private Map<String, String> statusLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (ExpenseStatus s : ExpenseStatus.values()) {
            m.put(s.name(), msg("exp.status." + s.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private Map<String, String> paymentMethodLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(PaymentMethod.CASH.name(), msg("inv.pay.cash"));
        labels.put(PaymentMethod.BANK_TRANSFER.name(), msg("inv.pay.bankTransfer"));
        labels.put(PaymentMethod.MOBILE_MONEY.name(), msg("inv.pay.mtnMobileMoney"));
        labels.put(PaymentMethod.CARD.name(), msg("inv.pay.card"));
        labels.put(PaymentMethod.CHECK.name(), msg("inv.pay.check"));
        return labels;
    }

    /** Cash on hand and bank accounts — the money an expense can be paid from. */
    private List<Account> paymentAccounts() {
        return accountingService.listActiveAccounts().stream()
                .filter(a -> a.getType() == AccountType.ASSET)
                .filter(a -> a.getCode().startsWith("10") || a.getCode().startsWith("11"))
                .toList();
    }

    private List<Account> expenseAccounts() {
        return accountingService.listActiveAccounts().stream()
                .filter(a -> a.getType() == AccountType.EXPENSE || a.getType() == AccountType.ASSET)
                .toList();
    }

    private void addFormContext(Model model, String mode, Long editingId) {
        model.addAttribute("mode", mode);
        model.addAttribute("editingId", editingId);
        model.addAttribute("vendors", vendorService.listActiveVendors());
        model.addAttribute("expenseAccounts", expenseAccounts());
        model.addAttribute("paymentAccounts", paymentAccounts());
        model.addAttribute("paymentMethods", paymentMethodLabels());
        model.addAttribute("taxRates", taxRateService.listActive());
        model.addAttribute("baseCurrency", expenseService.baseCurrency());
        model.addAttribute("defaultVatRate", taxRateService.defaultRateValue());
        model.addAttribute("nextNumber", expenseService.previewNextNumber());
    }

    // ----- List -----

    @GetMapping
    public String expenses(@RequestParam(value = "q", required = false) String q,
                           @RequestParam(value = "status", required = false) String status,
                           @RequestParam(value = "vendor", required = false) Long vendorId,
                           @RequestParam(value = "sort", defaultValue = "expenseDate") String sort,
                           @RequestParam(value = "dir", defaultValue = "desc") String dir,
                           @RequestParam(value = "page", defaultValue = "0") int page,
                           Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "expenseDate", "expenseDate", "expenseNo",
                "payeeName", "total", "status");
        model.addAttribute("expenses", expenseService.list(q, status, vendorId, null, null,
                PageRequest.of(page, PAGE_SIZE, sp.sort())));
        model.addAttribute("summary", expenseService.summary());
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("baseCurrency", expenseService.baseCurrency());
        model.addAttribute("q", q);
        model.addAttribute("status", status == null ? "" : status);
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
        SortSpec.addListContext(model, "/purchases/expenses", fq.isEmpty() ? "" : "?" + fq, sp);
        return "purchases/expenses";
    }

    // ----- Create / edit -----

    @GetMapping("/new")
    public String newExpense(@RequestParam(value = "vendor", required = false) Long vendorId,
                             Model model) {
        ExpenseForm form = ExpenseForm.empty(taxRateService.defaultRateValue());
        form.setCurrencyCode(expenseService.baseCurrency());
        if (vendorId != null) {
            form.setVendorId(vendorId);
        }
        addFormContext(model, "create", null);
        model.addAttribute("form", form);
        return "purchases/expense-form";
    }

    @GetMapping("/{id}/edit")
    public String editExpense(@PathVariable Long id, Model model, RedirectAttributes ra) {
        Expense expense = expenseService.get(id);
        if (expense == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("exp.notFound", null));
            return "redirect:/purchases/expenses";
        }
        if (!expense.isEditable()) {
            ra.addFlashAttribute("flashMessage", errorFlash("exp.notEditable", expense.getExpenseNo()));
            return "redirect:/purchases/expenses/" + id;
        }
        ExpenseForm form = new ExpenseForm();
        form.setVendorId(expense.getVendorId());
        form.setPayeeName(expense.getPayeeName());
        form.setExpenseDate(expense.getExpenseDate());
        form.setPaymentAccountId(expense.getPaymentAccountId());
        form.setPaymentMethod(expense.getPaymentMethod() == null
                ? null : expense.getPaymentMethod().name());
        form.setReference(expense.getReference());
        form.setCurrencyCode(expense.getCurrencyCode());
        form.setMemo(expense.getMemo());
        form.setNotes(expense.getNotes());
        for (int i = 0; i < expense.getLines().size(); i++) {
            ExpenseLine src = expense.getLines().get(i);
            ExpenseForm.Line line = form.getLines().get(i);
            line.setDescription(src.getDescription());
            line.setAmount(src.getAmount());
            line.setTaxRate(src.getTaxRate());
            line.setTaxRateId(src.getTaxRateId());
            line.setExpenseAccountId(src.getExpenseAccountId());
        }
        addFormContext(model, "edit", id);
        model.addAttribute("form", form);
        return "purchases/expense-form";
    }

    @PostMapping
    public String create(@ModelAttribute("form") ExpenseForm form, BindingResult br,
                         Model model, RedirectAttributes ra) {
        validate(form, br);
        if (br.hasErrors()) {
            addFormContext(model, "create", null);
            return "purchases/expense-form";
        }
        try {
            Expense saved = expenseService.save(form, null, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("exp.saved", saved.getExpenseNo()));
            return "redirect:/purchases/expenses/" + saved.getId();
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("exp.actionFailed", ex.getMessage()));
            return "redirect:/purchases/expenses/new";
        }
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute("form") ExpenseForm form,
                         BindingResult br, Model model, RedirectAttributes ra) {
        validate(form, br);
        if (br.hasErrors()) {
            addFormContext(model, "edit", id);
            return "purchases/expense-form";
        }
        try {
            Expense saved = expenseService.save(form, id, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("exp.saved", saved.getExpenseNo()));
            return "redirect:/purchases/expenses/" + saved.getId();
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("exp.actionFailed", ex.getMessage()));
            return "redirect:/purchases/expenses/" + id;
        }
    }

    private void validate(ExpenseForm form, BindingResult br) {
        boolean noVendor = form.getVendorId() == null;
        boolean noPayee = form.getPayeeName() == null || form.getPayeeName().isBlank();
        if (noVendor && noPayee) {
            br.rejectValue("payeeName", "exp.payeeRequired");
        }
        if (form.getExpenseDate() == null) {
            br.rejectValue("expenseDate", "exp.dateRequired");
        }
        if (form.getPaymentAccountId() == null) {
            br.rejectValue("paymentAccountId", "exp.paymentAccountRequired");
        }
        if (!form.hasContent()) {
            br.rejectValue("lines", "exp.noLines");
        }
    }

    // ----- View -----

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model, RedirectAttributes ra) {
        Expense expense = expenseService.get(id);
        if (expense == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("exp.notFound", null));
            return "redirect:/purchases/expenses";
        }
        model.addAttribute("expense", expense);
        model.addAttribute("journal", expenseService.journalFor(expense));
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("methodLabels", paymentMethodLabels());
        model.addAttribute("baseCurrency", expenseService.baseCurrency());
        return "purchases/expense-view";
    }

    // ----- Lifecycle -----

    @PostMapping("/{id}/post")
    public String post(@PathVariable Long id, RedirectAttributes ra) {
        try {
            Expense posted = expenseService.post(id);
            ra.addFlashAttribute("flashMessage", flash("exp.posted", posted.getExpenseNo()));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("exp.actionFailed", ex.getMessage()));
        }
        return "redirect:/purchases/expenses/" + id;
    }

    @PostMapping("/{id}/void")
    public String voidExpense(@PathVariable Long id,
                              @RequestParam(value = "reason", required = false) String reason,
                              RedirectAttributes ra) {
        try {
            expenseService.voidExpense(id, reason);
            ra.addFlashAttribute("flashMessage", flash("exp.voided", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("exp.actionFailed", ex.getMessage()));
        }
        return "redirect:/purchases/expenses/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            expenseService.delete(id);
            ra.addFlashAttribute("flashMessage", flash("exp.deleted", null));
            return "redirect:/purchases/expenses";
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("exp.actionFailed", ex.getMessage()));
            return "redirect:/purchases/expenses/" + id;
        }
    }
}
