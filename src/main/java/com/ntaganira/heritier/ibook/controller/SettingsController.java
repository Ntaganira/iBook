/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : SettingsController.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : System settings web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.*;
import com.ntaganira.heritier.ibook.entity.*;
import com.ntaganira.heritier.ibook.service.AuditService;
import com.ntaganira.heritier.ibook.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@PreAuthorize("hasRole('ADMIN')")
public class SettingsController {

    private static final int PAGE_SIZE = 20;

    private final SettingsService settingsService;
    private final MessageSource messageSource;

    public SettingsController(SettingsService settingsService, MessageSource messageSource) {
        this.settingsService = settingsService;
        this.messageSource = messageSource;
    }

    private String msg(String key) {
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
    }

    private Map<String, String> flash(String key, String detail) {
        return Map.of("title", msg(key), "detail", detail == null ? "" : detail, "type", "success");
    }

    @GetMapping("/settings")
    public String settings() {
        return "settings/index";
    }

    // ----- Company profile -----

    @GetMapping("/settings/company")
    public String company(Model model) {
        Company c = settingsService.getCompany();
        model.addAttribute("company", c);
        model.addAttribute("currencies", settingsService.listCurrenciesForSelect());
        model.addAttribute("months", MONTHS);
        model.addAttribute("form", new CompanyForm(c.getName(), c.getLegalName(), c.getTin(), c.getEmail(),
                c.getPhone(), c.getWebsite(), c.getAddress(), c.getCity(), c.getCountry(),
                c.getCurrencyCode(), c.getLogoUrl(), c.getFiscalYearStart()));
        return "settings/company";
    }

    @PostMapping("/settings/company")
    public String saveCompany(@Valid @ModelAttribute("form") CompanyForm form,
                              BindingResult bindingResult, RedirectAttributes redirectAttributes, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("company", settingsService.getCompany());
            model.addAttribute("currencies", settingsService.listCurrenciesForSelect());
            model.addAttribute("months", MONTHS);
            return "settings/company";
        }
        settingsService.saveCompany(form);
        redirectAttributes.addFlashAttribute("flashMessage", flash("set.saved", form.name()));
        return "redirect:/settings/company";
    }

    // ----- Fiscal year -----

    @GetMapping("/settings/fiscal-year")
    public String fiscalYear(Model model) {
        model.addAttribute("company", settingsService.getCompany());
        model.addAttribute("months", MONTHS);
        return "settings/fiscal-year";
    }

    @PostMapping("/settings/fiscal-year")
    public String saveFiscalYear(@RequestParam("fiscalYearStart") String fiscalYearStart,
                                 RedirectAttributes redirectAttributes) {
        settingsService.saveFiscalYear(fiscalYearStart);
        redirectAttributes.addFlashAttribute("flashMessage", flash("set.saved", ""));
        return "redirect:/settings/fiscal-year";
    }

    // ----- Branches -----

    @GetMapping("/settings/branches")
    public String branches(@RequestParam(value = "sort", defaultValue = "name") String sort,
                           @RequestParam(value = "dir", defaultValue = "asc") String dir,
                           @RequestParam(value = "page", defaultValue = "0") int page,
                           Model model) {
        SortSpec sp = resolveSortSpec(sort, dir, "name", "name", "code", "city", "defaultBranch", "active");
        Page<Branch> result = settingsService.listBranches(PageRequest.of(page, PAGE_SIZE, sp.sort()));
        model.addAttribute("branches", result);
        addListContext(model, "/settings/branches", "", sp.field(), sp.dir());
        return "settings/branches";
    }

    @GetMapping("/settings/branches/new")
    public String newBranch(Model model) {
        model.addAttribute("mode", "create");
        model.addAttribute("editingId", null);
        model.addAttribute("form", new BranchForm("", "", null, null, null, null, null, null, false, true));
        return "settings/branch-form";
    }

    @GetMapping("/settings/branches/{id}/edit")
    public String editBranch(@PathVariable Long id, Model model) {
        Branch b = settingsService.getBranch(id);
        if (b == null) {
            return "redirect:/settings/branches";
        }
        model.addAttribute("mode", "edit");
        model.addAttribute("editingId", id);
        model.addAttribute("form", new BranchForm(b.getName(), b.getCode(), b.getAddress(), b.getCity(),
                b.getCountry(), b.getContactPerson(), b.getPhone(), b.getEmail(),
                b.isDefaultBranch(), b.isActive()));
        return "settings/branch-form";
    }

    @PostMapping("/settings/branches")
    public String createBranch(@Valid @ModelAttribute("form") BranchForm form,
                               BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (settingsService.branchCodeExists(form.code(), null)) {
            bindingResult.rejectValue("code", "set.branch.code.exists");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("mode", "create");
            model.addAttribute("editingId", null);
            return "settings/branch-form";
        }
        Branch saved = settingsService.saveBranch(form, null);
        redirectAttributes.addFlashAttribute("flashMessage", flash("set.branch.saved", saved.getName()));
        return "redirect:/settings/branches";
    }

    @PostMapping("/settings/branches/{id}")
    public String updateBranch(@PathVariable Long id, @Valid @ModelAttribute("form") BranchForm form,
                               BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (settingsService.branchCodeExists(form.code(), id)) {
            bindingResult.rejectValue("code", "set.branch.code.exists");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("mode", "edit");
            model.addAttribute("editingId", id);
            return "settings/branch-form";
        }
        Branch saved = settingsService.saveBranch(form, id);
        redirectAttributes.addFlashAttribute("flashMessage", flash("set.branch.saved", saved.getName()));
        return "redirect:/settings/branches";
    }

    @PostMapping("/settings/branches/{id}/toggle")
    public String toggleBranch(@PathVariable Long id) {
        settingsService.toggleBranch(id);
        return "redirect:/settings/branches";
    }

    // ----- Users -----

    @GetMapping("/settings/users")
    public String users(@RequestParam(value = "q", required = false) String q,
                        @RequestParam(value = "role", required = false) Long roleId,
                        @RequestParam(value = "sort", defaultValue = "username") String sort,
                        @RequestParam(value = "dir", defaultValue = "asc") String dir,
                        @RequestParam(value = "page", defaultValue = "0") int page,
                        Model model) {
        SortSpec sp = resolveSortSpec(sort, dir, "username", "username", "email", "firstName", "lastName", "enabled", "createdAt");
        Page<User> result = settingsService.listUsers(q, roleId, PageRequest.of(page, PAGE_SIZE, sp.sort()));
        model.addAttribute("users", result);
        model.addAttribute("roles", settingsService.listRolesForSelect());
        model.addAttribute("q", q);
        model.addAttribute("roleFilter", roleId);
        String filterQuery = (q != null && !q.isBlank()) || roleId != null
                ? "?q=" + (q == null ? "" : UriUtils.encodeQueryParam(q, StandardCharsets.UTF_8))
                + (roleId != null ? "&role=" + roleId : "")
                : "";
        addListContext(model, "/settings/users", filterQuery, sp.field(), sp.dir());
        return "settings/users";
    }

    @GetMapping("/settings/users/new")
    public String newUser(Model model) {
        model.addAttribute("mode", "create");
        model.addAttribute("editingId", null);
        model.addAttribute("branches", settingsService.listBranchesForSelect());
        model.addAttribute("roles", settingsService.listRolesForSelect());
        model.addAttribute("form", new CreateUserForm("", "", "", "", null, null, null, true, "", ""));
        return "settings/user-form";
    }

    @GetMapping("/settings/users/{id}/edit")
    public String editUser(@PathVariable Long id, Model model) {
        User u = settingsService.getUser(id);
        if (u == null) {
            return "redirect:/settings/users";
        }
        Long roleId = u.getRoles().stream().findFirst().map(Role::getId).orElse(null);
        model.addAttribute("mode", "edit");
        model.addAttribute("editingId", id);
        model.addAttribute("branches", settingsService.listBranchesForSelect());
        model.addAttribute("roles", settingsService.listRolesForSelect());
        model.addAttribute("form", new EditUserForm(u.getFirstName(), u.getLastName(), u.getEmail(),
                u.getUsername(), u.getPhone(), null, roleId, u.isEnabled(), null, null));
        return "settings/user-form";
    }

    @PostMapping("/settings/users")
    public String createUser(@Valid @ModelAttribute("form") CreateUserForm form,
                             BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (!form.passwordsMatch()) {
            bindingResult.rejectValue("confirmPassword", "set.user.passwordMismatch");
        }
        if (settingsService.emailExists(form.email(), null)) {
            bindingResult.rejectValue("email", "set.user.email.exists");
        }
        if (settingsService.usernameExists(form.username(), null)) {
            bindingResult.rejectValue("username", "set.user.username.exists");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("mode", "create");
            model.addAttribute("editingId", null);
            model.addAttribute("branches", settingsService.listBranchesForSelect());
            model.addAttribute("roles", settingsService.listRolesForSelect());
            return "settings/user-form";
        }
        User saved = settingsService.createUser(form);
        redirectAttributes.addFlashAttribute("flashMessage", flash("set.user.saved", saved.getEmail()));
        return "redirect:/settings/users";
    }

    @PostMapping("/settings/users/{id}")
    public String updateUser(@PathVariable Long id, @Valid @ModelAttribute("form") EditUserForm form,
                             BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (form.wantsPasswordChange() && !form.passwordsMatch()) {
            bindingResult.rejectValue("confirmPassword", "set.user.passwordMismatch");
        }
        if (settingsService.emailExists(form.email(), id)) {
            bindingResult.rejectValue("email", "set.user.email.exists");
        }
        if (settingsService.usernameExists(form.username(), id)) {
            bindingResult.rejectValue("username", "set.user.username.exists");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("mode", "edit");
            model.addAttribute("editingId", id);
            model.addAttribute("branches", settingsService.listBranchesForSelect());
            model.addAttribute("roles", settingsService.listRolesForSelect());
            return "settings/user-form";
        }
        User saved = settingsService.updateUser(id, form);
        redirectAttributes.addFlashAttribute("flashMessage", flash("set.user.saved", saved.getEmail()));
        return "redirect:/settings/users";
    }

    @PostMapping("/settings/users/{id}/toggle")
    public String toggleUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User u = settingsService.getUser(id);
        if (u != null && AuditService.currentUsername().equalsIgnoreCase(u.getUsername())) {
            redirectAttributes.addFlashAttribute("flashMessage", flash("set.user.cannotDisableSelf", ""));
            return "redirect:/settings/users";
        }
        settingsService.toggleUser(id);
        return "redirect:/settings/users";
    }

    // ----- Roles -----

    @GetMapping("/settings/roles")
    public String roles(@RequestParam(value = "sort", defaultValue = "name") String sort,
                        @RequestParam(value = "dir", defaultValue = "asc") String dir,
                        @RequestParam(value = "page", defaultValue = "0") int page,
                        Model model) {
        SortSpec sp = resolveSortSpec(sort, dir, "name", "name", "description");
        Page<Role> result = settingsService.listRoles(PageRequest.of(page, PAGE_SIZE, sp.sort()));
        model.addAttribute("roles", result);
        addListContext(model, "/settings/roles", "", sp.field(), sp.dir());
        return "settings/roles";
    }

    @GetMapping("/settings/roles/new")
    public String newRole(Model model) {
        List<Permission> permissions = settingsService.listPermissions();
        model.addAttribute("mode", "create");
        model.addAttribute("editingId", null);
        model.addAttribute("permissions", permissions);
        model.addAttribute("grouped", groupPermissions(permissions));
        model.addAttribute("selected", Set.of());
        model.addAttribute("form", new RoleForm("", null, List.of()));
        return "settings/role-form";
    }

    @GetMapping("/settings/roles/{id}/edit")
    public String editRole(@PathVariable Long id, Model model) {
        Role r = settingsService.getRole(id);
        if (r == null) {
            return "redirect:/settings/roles";
        }
        List<Permission> permissions = settingsService.listPermissions();
        model.addAttribute("mode", "edit");
        model.addAttribute("editingId", id);
        model.addAttribute("permissions", permissions);
        model.addAttribute("grouped", groupPermissions(permissions));
        model.addAttribute("selected", r.getPermissions().stream().map(Permission::getId).collect(Collectors.toSet()));
        model.addAttribute("form", new RoleForm(r.getName(), r.getDescription(), List.of()));
        return "settings/role-form";
    }

    @PostMapping("/settings/roles")
    public String createRole(@Valid @ModelAttribute("form") RoleForm form,
                             BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (settingsService.roleNameExists(form.name(), null)) {
            bindingResult.rejectValue("name", "set.role.name.exists");
        }
        if (bindingResult.hasErrors()) {
            List<Permission> permissions = settingsService.listPermissions();
            model.addAttribute("mode", "create");
            model.addAttribute("editingId", null);
            model.addAttribute("permissions", permissions);
            model.addAttribute("grouped", groupPermissions(permissions));
            model.addAttribute("selected", form.permissionIds().stream().collect(Collectors.toSet()));
            return "settings/role-form";
        }
        Role saved = settingsService.saveRole(form, null);
        redirectAttributes.addFlashAttribute("flashMessage", flash("set.role.saved", saved.getName()));
        return "redirect:/settings/roles";
    }

    @PostMapping("/settings/roles/{id}")
    public String updateRole(@PathVariable Long id, @Valid @ModelAttribute("form") RoleForm form,
                             BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (settingsService.roleNameExists(form.name(), id)) {
            bindingResult.rejectValue("name", "set.role.name.exists");
        }
        if (bindingResult.hasErrors()) {
            List<Permission> permissions = settingsService.listPermissions();
            model.addAttribute("mode", "edit");
            model.addAttribute("editingId", id);
            model.addAttribute("permissions", permissions);
            model.addAttribute("grouped", groupPermissions(permissions));
            model.addAttribute("selected", form.permissionIds().stream().collect(Collectors.toSet()));
            return "settings/role-form";
        }
        Role saved = settingsService.saveRole(form, id);
        redirectAttributes.addFlashAttribute("flashMessage", flash("set.role.saved", saved.getName()));
        return "redirect:/settings/roles";
    }

    // ----- Permissions -----

    @GetMapping("/settings/permissions")
    public String permissions(Model model) {
        List<Permission> permissions = settingsService.listPermissions();
        model.addAttribute("modules", groupPermissions(permissions));
        model.addAttribute("total", permissions.size());
        return "settings/permissions";
    }

    // ----- Approval workflows -----

    @GetMapping("/settings/workflows")
    public String workflows(@RequestParam(value = "sort", defaultValue = "name") String sort,
                            @RequestParam(value = "dir", defaultValue = "asc") String dir,
                            @RequestParam(value = "page", defaultValue = "0") int page,
                            Model model) {
        SortSpec sp = resolveSortSpec(sort, dir, "name", "name", "module", "triggerEvent", "active");
        Page<ApprovalWorkflow> result = settingsService.listWorkflows(PageRequest.of(page, PAGE_SIZE, sp.sort()));
        model.addAttribute("workflows", result);
        addListContext(model, "/settings/workflows", "", sp.field(), sp.dir());
        return "settings/workflows";
    }

    @GetMapping("/settings/workflows/new")
    public String newWorkflow(Model model) {
        model.addAttribute("mode", "create");
        model.addAttribute("editingId", null);
        model.addAttribute("form", new WorkflowForm("", "Sales", "Invoice created", 1, 2, true, null));
        return "settings/workflow-form";
    }

    @GetMapping("/settings/workflows/{id}/edit")
    public String editWorkflow(@PathVariable Long id, Model model) {
        ApprovalWorkflow wf = settingsService.getWorkflow(id);
        if (wf == null) {
            return "redirect:/settings/workflows";
        }
        model.addAttribute("mode", "edit");
        model.addAttribute("editingId", id);
        model.addAttribute("form", new WorkflowForm(wf.getName(), wf.getModule(), wf.getTriggerEvent(),
                wf.getSteps(), wf.getDaysEach(), wf.isActive(), wf.getDescription()));
        return "settings/workflow-form";
    }

    @PostMapping("/settings/workflows")
    public String createWorkflow(@Valid @ModelAttribute("form") WorkflowForm form,
                                 BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("mode", "create");
            model.addAttribute("editingId", null);
            return "settings/workflow-form";
        }
        ApprovalWorkflow saved = settingsService.saveWorkflow(form, null);
        redirectAttributes.addFlashAttribute("flashMessage", flash("set.workflow.saved", saved.getName()));
        return "redirect:/settings/workflows";
    }

    @PostMapping("/settings/workflows/{id}")
    public String updateWorkflow(@PathVariable Long id, @Valid @ModelAttribute("form") WorkflowForm form,
                                 BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("mode", "edit");
            model.addAttribute("editingId", id);
            return "settings/workflow-form";
        }
        ApprovalWorkflow saved = settingsService.saveWorkflow(form, id);
        redirectAttributes.addFlashAttribute("flashMessage", flash("set.workflow.saved", saved.getName()));
        return "redirect:/settings/workflows";
    }

    // ----- Currencies -----

    @GetMapping("/settings/currencies")
    public String currencies(@RequestParam(value = "sort", defaultValue = "code") String sort,
                             @RequestParam(value = "dir", defaultValue = "asc") String dir,
                             @RequestParam(value = "page", defaultValue = "0") int page,
                             Model model) {
        SortSpec sp = resolveSortSpec(sort, dir, "code", "code", "name", "decimals", "exchangeRateToBase", "enabled");
        Page<Currency> result = settingsService.listCurrencies(PageRequest.of(page, PAGE_SIZE, sp.sort()));
        model.addAttribute("currencies", result);
        addListContext(model, "/settings/currencies", "", sp.field(), sp.dir());
        return "settings/currencies";
    }

    @GetMapping("/settings/currencies/new")
    public String newCurrency(Model model) {
        model.addAttribute("mode", "create");
        model.addAttribute("editingId", null);
        model.addAttribute("form", new CurrencyForm("", "", null, 2, java.math.BigDecimal.ONE, false, true));
        return "settings/currency-form";
    }

    @GetMapping("/settings/currencies/{id}/edit")
    public String editCurrency(@PathVariable Long id, Model model) {
        Currency c = settingsService.getCurrency(id);
        if (c == null) {
            return "redirect:/settings/currencies";
        }
        model.addAttribute("mode", "edit");
        model.addAttribute("editingId", id);
        model.addAttribute("form", new CurrencyForm(c.getCode(), c.getName(), c.getSymbol(), c.getDecimals(),
                c.getExchangeRateToBase(), c.isBase(), c.isEnabled()));
        return "settings/currency-form";
    }

    @PostMapping("/settings/currencies")
    public String createCurrency(@Valid @ModelAttribute("form") CurrencyForm form,
                                 BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (settingsService.currencyCodeExists(form.code(), null)) {
            bindingResult.rejectValue("code", "set.currency.code.exists");
        }
        if (form.base() && !form.enabled()) {
            bindingResult.rejectValue("enabled", "set.currency.baseDisabled");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("mode", "create");
            model.addAttribute("editingId", null);
            return "settings/currency-form";
        }
        Currency saved = settingsService.saveCurrency(form, null);
        redirectAttributes.addFlashAttribute("flashMessage", flash("set.currency.saved", saved.getCode()));
        return "redirect:/settings/currencies";
    }

    @PostMapping("/settings/currencies/{id}")
    public String updateCurrency(@PathVariable Long id, @Valid @ModelAttribute("form") CurrencyForm form,
                                 BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (settingsService.currencyCodeExists(form.code(), id)) {
            bindingResult.rejectValue("code", "set.currency.code.exists");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("mode", "edit");
            model.addAttribute("editingId", id);
            return "settings/currency-form";
        }
        Currency saved = settingsService.saveCurrency(form, id);
        redirectAttributes.addFlashAttribute("flashMessage", flash("set.currency.saved", saved.getCode()));
        return "redirect:/settings/currencies";
    }

    @PostMapping("/settings/currencies/{id}/toggle")
    public String toggleCurrency(@PathVariable Long id) {
        settingsService.toggleCurrency(id);
        return "redirect:/settings/currencies";
    }

    // ----- Exchange rates -----

    @GetMapping("/settings/exchange-rates")
    public String exchangeRates(@RequestParam(value = "sort", defaultValue = "effectiveDate") String sort,
                                @RequestParam(value = "dir", defaultValue = "desc") String dir,
                                @RequestParam(value = "page", defaultValue = "0") int page,
                                Model model) {
        SortSpec sp = resolveSortSpec(sort, dir, "effectiveDate", "effectiveDate", "baseCurrency", "quoteCurrency", "rate");
        Page<ExchangeRate> rates = settingsService.listRates(PageRequest.of(page, PAGE_SIZE, sp.sort()));
        model.addAttribute("rates", rates);
        model.addAttribute("currencies", settingsService.listCurrenciesForSelect());
        model.addAttribute("baseCurrency", getBaseCurrencyCode());
        model.addAttribute("form", new ExchangeRateForm(getBaseCurrencyCode(), null, null, java.time.LocalDate.now()));
        addListContext(model, "/settings/exchange-rates", "", sp.field(), sp.dir());
        return "settings/exchange-rates";
    }

    @PostMapping("/settings/exchange-rates")
    public String createExchangeRate(@Valid @ModelAttribute("form") ExchangeRateForm form,
                                     BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (form.quoteCurrency() != null && form.quoteCurrency().equalsIgnoreCase(form.baseCurrency())) {
            bindingResult.rejectValue("quoteCurrency", "set.rate.samePair");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("rates", settingsService.listRates(PageRequest.of(0, PAGE_SIZE)));
            model.addAttribute("currencies", settingsService.listCurrenciesForSelect());
            model.addAttribute("baseCurrency", getBaseCurrencyCode());
            return "settings/exchange-rates";
        }
        ExchangeRate saved = settingsService.saveRate(form);
        redirectAttributes.addFlashAttribute("flashMessage", flash("set.rate.saved",
                saved.getQuoteCurrency() + "/" + saved.getBaseCurrency()));
        return "redirect:/settings/exchange-rates";
    }

    // ----- Numbering sequences -----

    @GetMapping("/settings/numbering")
    public String numbering(@RequestParam(value = "sort", defaultValue = "name") String sort,
                            @RequestParam(value = "dir", defaultValue = "asc") String dir,
                            @RequestParam(value = "page", defaultValue = "0") int page,
                            Model model) {
        SortSpec sp = resolveSortSpec(sort, dir, "name", "name", "docType", "prefix", "nextNumber", "resetYearly", "active");
        Page<NumberingSequence> result = settingsService.listNumbering(PageRequest.of(page, PAGE_SIZE, sp.sort()));
        model.addAttribute("sequences", result);
        addListContext(model, "/settings/numbering", "", sp.field(), sp.dir());
        return "settings/numbering";
    }

    @GetMapping("/settings/numbering/new")
    public String newNumbering(Model model) {
        model.addAttribute("mode", "create");
        model.addAttribute("editingId", null);
        model.addAttribute("form", new NumberingForm("", "INVOICE", null, null, 4, 1, true, true));
        return "settings/numbering-form";
    }

    @GetMapping("/settings/numbering/{id}/edit")
    public String editNumbering(@PathVariable Long id, Model model) {
        NumberingSequence seq = settingsService.getNumbering(id);
        if (seq == null) {
            return "redirect:/settings/numbering";
        }
        model.addAttribute("mode", "edit");
        model.addAttribute("editingId", id);
        model.addAttribute("form", new NumberingForm(seq.getName(), seq.getDocType(), seq.getPrefix(),
                seq.getSuffix(), seq.getPadding(), seq.getNextNumber(), seq.isResetYearly(), seq.isActive()));
        return "settings/numbering-form";
    }

    @PostMapping("/settings/numbering")
    public String createNumbering(@Valid @ModelAttribute("form") NumberingForm form,
                                  BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (settingsService.numberingNameExists(form.name(), null)) {
            bindingResult.rejectValue("name", "set.numbering.name.exists");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("mode", "create");
            model.addAttribute("editingId", null);
            return "settings/numbering-form";
        }
        NumberingSequence saved = settingsService.saveNumbering(form, null);
        redirectAttributes.addFlashAttribute("flashMessage", flash("set.numbering.saved", saved.getName()));
        return "redirect:/settings/numbering";
    }

    @PostMapping("/settings/numbering/{id}")
    public String updateNumbering(@PathVariable Long id, @Valid @ModelAttribute("form") NumberingForm form,
                                  BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (settingsService.numberingNameExists(form.name(), id)) {
            bindingResult.rejectValue("name", "set.numbering.name.exists");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("mode", "edit");
            model.addAttribute("editingId", id);
            return "settings/numbering-form";
        }
        NumberingSequence saved = settingsService.saveNumbering(form, id);
        redirectAttributes.addFlashAttribute("flashMessage", flash("set.numbering.saved", saved.getName()));
        return "redirect:/settings/numbering";
    }

    // ----- Invoice templates -----

    @GetMapping("/settings/invoice-templates")
    public String invoiceTemplates(@RequestParam(value = "sort", defaultValue = "name") String sort,
                                   @RequestParam(value = "dir", defaultValue = "asc") String dir,
                                   @RequestParam(value = "page", defaultValue = "0") int page,
                                   Model model) {
        SortSpec sp = resolveSortSpec(sort, dir, "name", "name", "layout", "paperSize", "defaultTemplate", "active");
        Page<InvoiceTemplate> result = settingsService.listInvoiceTemplates(PageRequest.of(page, PAGE_SIZE, sp.sort()));
        model.addAttribute("templates", result);
        addListContext(model, "/settings/invoice-templates", "", sp.field(), sp.dir());
        return "settings/invoice-templates";
    }

    @GetMapping("/settings/invoice-templates/new")
    public String newInvoiceTemplate(Model model) {
        model.addAttribute("mode", "create");
        model.addAttribute("editingId", null);
        model.addAttribute("form", new InvoiceTemplateForm("", "modern", "#166534", "A4", false, true, true, true, true));
        return "settings/invoice-template-form";
    }

    @GetMapping("/settings/invoice-templates/{id}/edit")
    public String editInvoiceTemplate(@PathVariable Long id, Model model) {
        InvoiceTemplate t = settingsService.getInvoiceTemplate(id);
        if (t == null) {
            return "redirect:/settings/invoice-templates";
        }
        model.addAttribute("mode", "edit");
        model.addAttribute("editingId", id);
        model.addAttribute("form", new InvoiceTemplateForm(t.getName(), t.getLayout(), t.getAccentColor(),
                t.getPaperSize(), t.isDefaultTemplate(), t.isShowsLogo(), t.isShowsTaxSummary(),
                t.isIncludesTerms(), t.isActive()));
        return "settings/invoice-template-form";
    }

    @PostMapping("/settings/invoice-templates")
    public String createInvoiceTemplate(@Valid @ModelAttribute("form") InvoiceTemplateForm form,
                                        BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("mode", "create");
            model.addAttribute("editingId", null);
            return "settings/invoice-template-form";
        }
        InvoiceTemplate saved = settingsService.saveInvoiceTemplate(form, null);
        redirectAttributes.addFlashAttribute("flashMessage", flash("set.template.saved", saved.getName()));
        return "redirect:/settings/invoice-templates";
    }

    @PostMapping("/settings/invoice-templates/{id}")
    public String updateInvoiceTemplate(@PathVariable Long id, @Valid @ModelAttribute("form") InvoiceTemplateForm form,
                                        BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("mode", "edit");
            model.addAttribute("editingId", id);
            return "settings/invoice-template-form";
        }
        InvoiceTemplate saved = settingsService.saveInvoiceTemplate(form, id);
        redirectAttributes.addFlashAttribute("flashMessage", flash("set.template.saved", saved.getName()));
        return "redirect:/settings/invoice-templates";
    }

    @PostMapping("/settings/invoice-templates/{id}/default")
    public String setDefaultInvoiceTemplate(@PathVariable Long id) {
        settingsService.setDefaultInvoiceTemplate(id);
        return "redirect:/settings/invoice-templates";
    }

    // ----- Email templates -----

    @GetMapping("/settings/email-templates")
    public String emailTemplates(@RequestParam(value = "sort", defaultValue = "name") String sort,
                                 @RequestParam(value = "dir", defaultValue = "asc") String dir,
                                 @RequestParam(value = "page", defaultValue = "0") int page,
                                 Model model) {
        SortSpec sp = resolveSortSpec(sort, dir, "name", "name", "code", "contentType", "active");
        Page<EmailTemplate> result = settingsService.listEmailTemplates(PageRequest.of(page, PAGE_SIZE, sp.sort()));
        model.addAttribute("templates", result);
        addListContext(model, "/settings/email-templates", "", sp.field(), sp.dir());
        return "settings/email-templates";
    }

    @GetMapping("/settings/email-templates/new")
    public String newEmailTemplate(Model model) {
        model.addAttribute("mode", "create");
        model.addAttribute("editingId", null);
        model.addAttribute("form", new EmailTemplateForm("", "", "", "html", null, true));
        return "settings/email-template-form";
    }

    @GetMapping("/settings/email-templates/{id}/edit")
    public String editEmailTemplate(@PathVariable Long id, Model model) {
        EmailTemplate t = settingsService.getEmailTemplate(id);
        if (t == null) {
            return "redirect:/settings/email-templates";
        }
        model.addAttribute("mode", "edit");
        model.addAttribute("editingId", id);
        model.addAttribute("form", new EmailTemplateForm(t.getCode(), t.getName(), t.getSubject(),
                t.getContentType(), t.getBody(), t.isActive()));
        return "settings/email-template-form";
    }

    @PostMapping("/settings/email-templates")
    public String createEmailTemplate(@Valid @ModelAttribute("form") EmailTemplateForm form,
                                      BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (settingsService.emailTemplateCodeExists(form.code(), null)) {
            bindingResult.rejectValue("code", "set.template.code.exists");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("mode", "create");
            model.addAttribute("editingId", null);
            return "settings/email-template-form";
        }
        EmailTemplate saved = settingsService.saveEmailTemplate(form, null);
        redirectAttributes.addFlashAttribute("flashMessage", flash("set.template.saved", saved.getCode()));
        return "redirect:/settings/email-templates";
    }

    @PostMapping("/settings/email-templates/{id}")
    public String updateEmailTemplate(@PathVariable Long id, @Valid @ModelAttribute("form") EmailTemplateForm form,
                                      BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (settingsService.emailTemplateCodeExists(form.code(), id)) {
            bindingResult.rejectValue("code", "set.template.code.exists");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("mode", "edit");
            model.addAttribute("editingId", id);
            return "settings/email-template-form";
        }
        EmailTemplate saved = settingsService.saveEmailTemplate(form, id);
        redirectAttributes.addFlashAttribute("flashMessage", flash("set.template.saved", saved.getCode()));
        return "redirect:/settings/email-templates";
    }

    // ----- Notifications -----

    @GetMapping("/settings/notifications")
    public String notifications(@RequestParam(value = "sort", defaultValue = "moduleName") String sort,
                                @RequestParam(value = "dir", defaultValue = "asc") String dir,
                                @RequestParam(value = "page", defaultValue = "0") int page,
                                Model model) {
        SortSpec sp = resolveSortSpec(sort, dir, "moduleName", "moduleName", "moduleCode", "active");
        Page<NotificationPreference> result = settingsService.listNotifications(PageRequest.of(page, PAGE_SIZE, sp.sort()));
        model.addAttribute("preferences", result);
        addListContext(model, "/settings/notifications", "", sp.field(), sp.dir());
        return "settings/notifications";
    }

    @PostMapping("/settings/notifications/{moduleCode}")
    public String saveNotifications(@PathVariable String moduleCode,
                                    @RequestParam(value = "emailEnabled", required = false) Boolean emailEnabled,
                                    @RequestParam(value = "inAppEnabled", required = false) Boolean inAppEnabled,
                                    @RequestParam(value = "smsEnabled", required = false) Boolean smsEnabled,
                                    @RequestParam(value = "active", required = false) Boolean active,
                                    RedirectAttributes redirectAttributes) {
        settingsService.updateNotificationPreference(moduleCode, emailEnabled, inAppEnabled, smsEnabled, active);
        redirectAttributes.addFlashAttribute("flashMessage", flash("set.updated", moduleCode));
        return "redirect:/settings/notifications";
    }

    // ----- Security -----

    @GetMapping("/settings/security")
    public String security(Model model) {
        SecuritySettings s = settingsService.getSecuritySettings();
        model.addAttribute("roles", settingsService.listRolesForSelect());
        model.addAttribute("form", new SecuritySettingsForm(s.isTwoFactorRequired(), s.getPasswordMinLength(),
                s.isRequireUppercase(), s.isRequireNumber(), s.isRequireSpecialChar(), s.getPasswordExpiryDays(),
                s.getMaxLoginAttempts(), s.getLockoutMinutes(), s.getSessionTimeoutMinutes(),
                s.isAllowPublicSignup(), s.getDefaultRole(), s.isIpWhitelistEnabled()));
        return "settings/security";
    }

    @PostMapping("/settings/security")
    public String saveSecurity(@Valid @ModelAttribute("form") SecuritySettingsForm form,
                               BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("roles", settingsService.listRolesForSelect());
            return "settings/security";
        }
        settingsService.saveSecuritySettings(form);
        redirectAttributes.addFlashAttribute("flashMessage", flash("set.saved", ""));
        return "redirect:/settings/security";
    }

    // ----- Audit trail -----

    @GetMapping("/settings/audit")
    public String audit(@RequestParam(value = "q", required = false) String q,
                        @RequestParam(value = "sort", defaultValue = "createdAt") String sort,
                        @RequestParam(value = "dir", defaultValue = "desc") String dir,
                        @RequestParam(value = "page", defaultValue = "0") int page,
                        Model model) {
        SortSpec sp = resolveSortSpec(sort, dir, "createdAt", "createdAt", "actor", "module", "action", "target");
        model.addAttribute("logs", settingsService.listAudits(q, PageRequest.of(page, PAGE_SIZE, sp.sort())));
        model.addAttribute("q", q);
        addListContext(model, "/settings/audit",
                q != null && !q.isBlank() ? "?q=" + UriUtils.encodeQueryParam(q, StandardCharsets.UTF_8) : "",
                sp.field(), sp.dir());
        return "settings/audit";
    }

    private String getBaseCurrencyCode() {
        List<Currency> base = settingsService.allCurrencies().stream().filter(Currency::isBase).toList();
        if (!base.isEmpty()) {
            return base.get(0).getCode();
        }
        Company company = settingsService.getCompany();
        return company.getCurrencyCode() == null ? "RWF" : company.getCurrencyCode();
    }

    private Map<String, List<Permission>> groupPermissions(List<Permission> permissions) {
        Map<String, List<Permission>> grouped = new LinkedHashMap<>();
        for (Permission p : permissions) {
            String module = p.getCode().contains(".") ? p.getCode().substring(0, p.getCode().indexOf('.')) : "other";
            grouped.computeIfAbsent(module, k -> new java.util.ArrayList<>()).add(p);
        }
        return grouped;
    }

    private void addListContext(Model model, String basePath, String filterQuery, String sort, String dir) {
        model.addAttribute("basePath", basePath);
        model.addAttribute("filterQuery", filterQuery);
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);
    }

    private SortSpec resolveSortSpec(String sort, String dir, String defaultField, String... allowed) {
        if (sort == null || sort.isBlank()) {
            sort = defaultField;
        }
        boolean ok = false;
        for (String a : allowed) {
            if (a.equals(sort)) {
                ok = true;
                break;
            }
        }
        if (!ok) {
            sort = defaultField;
        }
        String d = "desc".equalsIgnoreCase(dir) ? "desc" : "asc";
        Sort sortDef = Sort.by(d.equals("desc") ? Sort.Direction.DESC : Sort.Direction.ASC, sort)
                .and(Sort.by(Sort.Direction.DESC, "id"));
        return new SortSpec(sort, d, sortDef);
    }

    private static final List<String> MONTHS = List.of(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December");

    private record SortSpec(String field, String dir, Sort sort) {}
}