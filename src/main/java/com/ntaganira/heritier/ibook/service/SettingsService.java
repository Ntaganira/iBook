/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : SettingsService.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : System settings domain service
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.*;
import com.ntaganira.heritier.ibook.entity.*;
import com.ntaganira.heritier.ibook.repository.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class SettingsService {

    private static final String MODULE = "settings";

    private final CompanyRepository companyRepository;
    private final BranchRepository branchRepository;
    private final CurrencyRepository currencyRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final NumberingSequenceRepository numberingSequenceRepository;
    private final ApprovalWorkflowRepository approvalWorkflowRepository;
    private final InvoiceTemplateRepository invoiceTemplateRepository;
    private final EmailTemplateRepository emailTemplateRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final SecuritySettingsRepository securitySettingsRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public SettingsService(CompanyRepository companyRepository,
                           BranchRepository branchRepository,
                           CurrencyRepository currencyRepository,
                           ExchangeRateRepository exchangeRateRepository,
                           NumberingSequenceRepository numberingSequenceRepository,
                           ApprovalWorkflowRepository approvalWorkflowRepository,
                           InvoiceTemplateRepository invoiceTemplateRepository,
                           EmailTemplateRepository emailTemplateRepository,
                           NotificationPreferenceRepository notificationPreferenceRepository,
                           SecuritySettingsRepository securitySettingsRepository,
                           UserRepository userRepository,
                           RoleRepository roleRepository,
                           PermissionRepository permissionRepository,
                           @Lazy PasswordEncoder passwordEncoder,
                           AuditService auditService) {
        this.companyRepository = companyRepository;
        this.branchRepository = branchRepository;
        this.currencyRepository = currencyRepository;
        this.exchangeRateRepository = exchangeRateRepository;
        this.numberingSequenceRepository = numberingSequenceRepository;
        this.approvalWorkflowRepository = approvalWorkflowRepository;
        this.invoiceTemplateRepository = invoiceTemplateRepository;
        this.emailTemplateRepository = emailTemplateRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.securitySettingsRepository = securitySettingsRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> listAudits(String q, Pageable pageable) {
        return auditService.list(q, pageable);
    }

    // ----- Company profile -----

    @Transactional(readOnly = true)
    public Company getCompany() {
        return companyRepository.findFirstByOrderByIdAsc().orElseGet(() -> {
            Company c = new Company();
            c.setName("My Business");
            c.setCurrencyCode("RWF");
            c.setFiscalYearStart("July");
            return c;
        });
    }

    @Transactional
    public Company saveCompany(CompanyForm form) {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElseGet(Company::new);
        company.setName(form.name().trim());
        company.setLegalName(trimToNull(form.legalName()));
        company.setTin(trimToNull(form.tin()));
        company.setEmail(trimToNull(form.email()));
        company.setPhone(trimToNull(form.phone()));
        company.setWebsite(trimToNull(form.website()));
        company.setAddress(trimToNull(form.address()));
        company.setCity(trimToNull(form.city()));
        company.setCountry(trimToNull(form.country()));
        company.setCurrencyCode(form.currencyCode().trim().toUpperCase(Locale.ROOT));
        company.setLogoUrl(trimToNull(form.logoUrl()));
        company.setFiscalYearStart(trimToNull(form.fiscalYearStart()));
        Company saved = companyRepository.save(company);
        auditService.log(MODULE, "UPDATE_COMPANY", "company#" + saved.getId(),
                "Updated company profile for " + saved.getName());
        return saved;
    }

    @Transactional
    public void saveFiscalYear(String fiscalYearStart) {
        Company company = getCompany();
        company.setFiscalYearStart(fiscalYearStart);
        companyRepository.save(company);
        auditService.log(MODULE, "UPDATE_FISCAL_YEAR", "company#" + company.getId(),
                "Fiscal year start changed to " + fiscalYearStart);
    }

    // ----- Branches -----

    @Transactional(readOnly = true)
    public Page<Branch> listBranches(Pageable pageable) {
        return branchRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<Branch> listBranchesForSelect() {
        return branchRepository.findAllActive();
    }

    @Transactional(readOnly = true)
    public Branch getBranch(Long id) {
        return branchRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean branchCodeExists(String code, Long id) {
        return id == null
                ? branchRepository.existsByCodeIgnoreCase(code)
                : branchRepository.existsByCodeIgnoreCaseAndIdNot(code, id);
    }

    @Transactional
    public Branch saveBranch(BranchForm form, Long id) {
        Branch branch = id == null ? new Branch() : branchRepository.findById(id).orElseThrow();
        branch.setName(form.name().trim());
        branch.setCode(form.code().trim().toUpperCase(Locale.ROOT));
        branch.setAddress(trimToNull(form.address()));
        branch.setCity(trimToNull(form.city()));
        branch.setCountry(trimToNull(form.country()));
        branch.setContactPerson(trimToNull(form.contactPerson()));
        branch.setPhone(trimToNull(form.phone()));
        branch.setEmail(trimToNull(form.email()));
        branch.setActive(form.active());
        if (form.defaultBranch()) {
            clearDefaultBranches();
            branch.setDefaultBranch(true);
        } else if (id != null && branch.isDefaultBranch() && branchRepository.findDefault().size() <= 1) {
            branch.setDefaultBranch(true);
        } else {
            branch.setDefaultBranch(false);
        }
        Branch saved = branchRepository.save(branch);
        auditService.log(MODULE, id == null ? "CREATE_BRANCH" : "UPDATE_BRANCH",
                "branch#" + saved.getId(), saved.getName() + " (" + saved.getCode() + ")");
        return saved;
    }

    @Transactional
    public void toggleBranch(Long id) {
        Branch branch = branchRepository.findById(id).orElse(null);
        if (branch == null) {
            return;
        }
        branch.setActive(!branch.isActive());
        if (!branch.isActive() && branch.isDefaultBranch()) {
            branchRepository.findDefault().stream()
                    .filter(b -> !b.getId().equals(id))
                    .findFirst()
                    .ifPresent(first -> first.setDefaultBranch(true));
            branch.setDefaultBranch(false);
        }
        branchRepository.save(branch);
        auditService.log(MODULE, "TOGGLE_BRANCH", "branch#" + id, branch.getName());
    }

    private void clearDefaultBranches() {
        branchRepository.findDefault().forEach(b -> b.setDefaultBranch(false));
    }

    // ----- Users -----

    @Transactional(readOnly = true)
    public Page<User> listUsers(String q, Long roleId, Pageable pageable) {
        return userRepository.search(q, roleId, pageable);
    }

    @Transactional(readOnly = true)
    public User getUser(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean emailExists(String email, Long id) {
        if (id == null) {
            return userRepository.existsByEmail(email.trim().toLowerCase());
        }
        User current = userRepository.findById(id).orElse(null);
        return current != null && !current.getEmail().equalsIgnoreCase(email.trim())
                && userRepository.existsByEmail(email.trim().toLowerCase());
    }

    @Transactional(readOnly = true)
    public boolean usernameExists(String username, Long id) {
        if (id == null) {
            return userRepository.existsByUsername(username.trim().toLowerCase());
        }
        User current = userRepository.findById(id).orElse(null);
        return current != null && !current.getUsername().equalsIgnoreCase(username.trim())
                && userRepository.existsByUsername(username.trim().toLowerCase());
    }

    @Transactional
    public User createUser(CreateUserForm form) {
        Role role = roleRepository.findById(form.roleId()).orElse(null);
        Branch branch = form.branchId() == null ? null : branchRepository.findById(form.branchId()).orElse(null);
        User user = User.builder()
                .firstName(form.firstName().trim())
                .lastName(form.lastName().trim())
                .email(form.email().trim().toLowerCase())
                .username(form.username().trim().toLowerCase())
                .password(passwordEncoder.encode(form.password()))
                .phone(trimToNull(form.phone()))
                .enabled(form.enabled())
                .emailVerified(true)
                .roles(new HashSet<>(Set.of(role)))
                .build();
        User saved = userRepository.save(user);
        auditService.log(MODULE, "CREATE_USER", "user#" + saved.getId(),
                saved.getFirstName() + " " + saved.getLastName()
                        + (role != null ? " (" + role.getName() + ")" : "")
                        + (branch != null ? " @ " + branch.getCode() : ""));
        return saved;
    }

    @Transactional
    public User updateUser(Long id, EditUserForm form) {
        User user = userRepository.findById(id).orElseThrow();
        Role role = roleRepository.findById(form.roleId()).orElse(null);
        Branch branch = form.branchId() == null ? null : branchRepository.findById(form.branchId()).orElse(null);
        user.setFirstName(form.firstName().trim());
        user.setLastName(form.lastName().trim());
        user.setEmail(form.email().trim().toLowerCase());
        user.setUsername(form.username().trim().toLowerCase());
        user.setPhone(trimToNull(form.phone()));
        user.setEnabled(form.enabled());
        if (role != null) {
            user.setRoles(new HashSet<>(Set.of(role)));
        }
        if (form.wantsPasswordChange() && form.passwordsMatch()) {
            user.setPassword(passwordEncoder.encode(form.newPassword()));
        }
        User saved = userRepository.save(user);
        auditService.log(MODULE, "UPDATE_USER", "user#" + id,
                saved.getFirstName() + " " + saved.getLastName()
                        + (role != null ? " (" + role.getName() + ")" : ""));
        return saved;
    }

    @Transactional
    public void toggleUser(Long id) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return;
        }
        user.setEnabled(!user.isEnabled());
        userRepository.save(user);
        auditService.log(MODULE, "TOGGLE_USER", "user#" + id,
                user.getEmail() + (user.isEnabled() ? " enabled" : " disabled"));
    }

    // ----- Roles & permissions -----

    @Transactional(readOnly = true)
    public Page<Role> listRoles(Pageable pageable) {
        return roleRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<Role> listRolesForSelect() {
        return roleRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Role getRole(Long id) {
        return roleRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean roleNameExists(String name, Long id) {
        return id == null
                ? roleRepository.existsByName(name.trim())
                : roleRepository.existsByNameAndIdNot(name.trim(), id);
    }

    @Transactional
    public Role saveRole(RoleForm form, Long id) {
        Role role = id == null ? new Role() : roleRepository.findById(id).orElseThrow();
        role.setName(form.name().trim());
        role.setDescription(trimToNull(form.description()));
        role.setPermissions(new HashSet<>(permissionRepository.findAllById(form.permissionIds())));
        Role saved = roleRepository.save(role);
        auditService.log(MODULE, id == null ? "CREATE_ROLE" : "UPDATE_ROLE",
                "role#" + saved.getId(), saved.getName() + " with " + saved.getPermissions().size() + " permissions");
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Permission> listPermissions() {
        return permissionRepository.findAllByOrderByCodeAsc();
    }

    // ----- Currencies -----

    @Transactional(readOnly = true)
    public Page<Currency> listCurrencies(Pageable pageable) {
        return currencyRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<Currency> allCurrencies() {
        return currencyRepository.findAll(Sort.by(Sort.Direction.ASC, "code"));
    }

    @Transactional(readOnly = true)
    public List<Currency> listCurrenciesForSelect() {
        return currencyRepository.findAllEnabled();
    }

    @Transactional(readOnly = true)
    public Currency getCurrency(Long id) {
        return currencyRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean currencyCodeExists(String code, Long id) {
        return id == null
                ? currencyRepository.existsByCodeIgnoreCase(code)
                : currencyRepository.existsByCodeIgnoreCaseAndIdNot(code, id);
    }

    @Transactional
    public Currency saveCurrency(CurrencyForm form, Long id) {
        Currency currency = id == null ? new Currency() : currencyRepository.findById(id).orElseThrow();
        currency.setCode(form.code().trim().toUpperCase(Locale.ROOT));
        currency.setName(form.name().trim());
        currency.setSymbol(trimToNull(form.symbol()));
        currency.setDecimals(form.decimals());
        if (form.base()) {
            currencyRepository.findBase().forEach(c -> c.setBase(false));
            currency.setBase(true);
        } else if (id != null && currency.isBase() && currencyRepository.findBase().size() <= 1) {
            currency.setBase(true);
        } else {
            currency.setBase(false);
        }
        BigDecimal rate = form.exchangeRateToBase() == null ? BigDecimal.ONE : form.exchangeRateToBase();
        currency.setExchangeRateToBase(form.base() || currency.isBase() ? BigDecimal.ONE : rate);
        currency.setEnabled(form.enabled());
        Currency saved = currencyRepository.save(currency);
        auditService.log(MODULE, id == null ? "CREATE_CURRENCY" : "UPDATE_CURRENCY",
                "currency#" + saved.getId(), saved.getCode() + " - " + saved.getName());
        return saved;
    }

    @Transactional
    public void toggleCurrency(Long id) {
        Currency currency = currencyRepository.findById(id).orElse(null);
        if (currency == null || currency.isBase()) {
            return;
        }
        currency.setEnabled(!currency.isEnabled());
        currencyRepository.save(currency);
        auditService.log(MODULE, "TOGGLE_CURRENCY", "currency#" + id, currency.getCode());
    }

    // ----- Exchange rates -----

    @Transactional(readOnly = true)
    public Page<ExchangeRate> listRates(Pageable pageable) {
        return exchangeRateRepository.findAll(pageable);
    }

    @Transactional
    public ExchangeRate saveRate(ExchangeRateForm form) {
        ExchangeRate rate = ExchangeRate.builder()
                .baseCurrency(form.baseCurrency().trim().toUpperCase(Locale.ROOT))
                .quoteCurrency(form.quoteCurrency().trim().toUpperCase(Locale.ROOT))
                .rate(form.rate())
                .effectiveDate(form.effectiveDate())
                .build();
        ExchangeRate saved = exchangeRateRepository.save(rate);
        auditService.log(MODULE, "CREATE_EXCHANGE_RATE", "exchangeRate#" + saved.getId(),
                saved.getQuoteCurrency() + "/" + saved.getBaseCurrency() + " = " + saved.getRate()
                        + " on " + saved.getEffectiveDate());
        return saved;
    }

    // ----- Numbering sequences -----

    @Transactional(readOnly = true)
    public Page<NumberingSequence> listNumbering(Pageable pageable) {
        return numberingSequenceRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public NumberingSequence getNumbering(Long id) {
        return numberingSequenceRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean numberingNameExists(String name, Long id) {
        return id == null
                ? numberingSequenceRepository.existsByNameIgnoreCase(name)
                : numberingSequenceRepository.existsByNameIgnoreCaseAndIdNot(name, id);
    }

    @Transactional
    public NumberingSequence saveNumbering(NumberingForm form, Long id) {
        NumberingSequence seq = id == null ? new NumberingSequence() : numberingSequenceRepository.findById(id).orElseThrow();
        seq.setName(form.name().trim());
        seq.setDocType(form.docType().trim());
        seq.setPrefix(trimToNull(form.prefix()));
        seq.setSuffix(trimToNull(form.suffix()));
        seq.setPadding(form.padding());
        seq.setNextNumber(form.nextNumber());
        seq.setResetYearly(form.resetYearly());
        seq.setActive(form.active());
        NumberingSequence saved = numberingSequenceRepository.save(seq);
        auditService.log(MODULE, id == null ? "CREATE_NUMBERING" : "UPDATE_NUMBERING",
                "numbering#" + saved.getId(), saved.getName() + " -> " + saved.previewNext());
        return saved;
    }

    // ----- Approval workflows -----

    @Transactional(readOnly = true)
    public Page<ApprovalWorkflow> listWorkflows(Pageable pageable) {
        return approvalWorkflowRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public ApprovalWorkflow getWorkflow(Long id) {
        return approvalWorkflowRepository.findById(id).orElse(null);
    }

    @Transactional
    public ApprovalWorkflow saveWorkflow(WorkflowForm form, Long id) {
        ApprovalWorkflow wf = id == null ? new ApprovalWorkflow() : approvalWorkflowRepository.findById(id).orElseThrow();
        wf.setName(form.name().trim());
        wf.setModule(form.module().trim());
        wf.setTriggerEvent(form.triggerEvent().trim());
        wf.setSteps(form.steps());
        wf.setDaysEach(form.daysEach());
        wf.setActive(form.active());
        wf.setDescription(trimToNull(form.description()));
        ApprovalWorkflow saved = approvalWorkflowRepository.save(wf);
        auditService.log(MODULE, id == null ? "CREATE_WORKFLOW" : "UPDATE_WORKFLOW",
                "workflow#" + saved.getId(), saved.getName() + " (" + saved.getModule() + ")");
        return saved;
    }

    // ----- Invoice templates -----

    @Transactional(readOnly = true)
    public Page<InvoiceTemplate> listInvoiceTemplates(Pageable pageable) {
        return invoiceTemplateRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public InvoiceTemplate getInvoiceTemplate(Long id) {
        return invoiceTemplateRepository.findById(id).orElse(null);
    }

    @Transactional
    public InvoiceTemplate saveInvoiceTemplate(InvoiceTemplateForm form, Long id) {
        InvoiceTemplate tpl = id == null ? new InvoiceTemplate() : invoiceTemplateRepository.findById(id).orElseThrow();
        tpl.setName(form.name().trim());
        tpl.setLayout(form.layout().trim());
        tpl.setAccentColor(trimToNull(form.accentColor()));
        tpl.setPaperSize(form.paperSize().trim());
        tpl.setShowsLogo(form.showsLogo());
        tpl.setShowsTaxSummary(form.showsTaxSummary());
        tpl.setIncludesTerms(form.includesTerms());
        tpl.setActive(form.active());
        if (form.defaultTemplate()) {
            invoiceTemplateRepository.findDefault().ifPresent(d -> d.setDefaultTemplate(false));
            tpl.setDefaultTemplate(true);
        } else if (id != null && tpl.isDefaultTemplate() && invoiceTemplateRepository.findDefault().isEmpty()) {
            tpl.setDefaultTemplate(true);
        } else {
            tpl.setDefaultTemplate(false);
        }
        InvoiceTemplate saved = invoiceTemplateRepository.save(tpl);
        auditService.log(MODULE, id == null ? "CREATE_TEMPLATE" : "UPDATE_TEMPLATE",
                "invoiceTemplate#" + saved.getId(), "Invoice template: " + saved.getName());
        return saved;
    }

    @Transactional
    public void setDefaultInvoiceTemplate(Long id) {
        InvoiceTemplate tpl = invoiceTemplateRepository.findById(id).orElse(null);
        if (tpl == null) {
            return;
        }
        invoiceTemplateRepository.findDefault().ifPresent(d -> d.setDefaultTemplate(false));
        tpl.setDefaultTemplate(true);
        invoiceTemplateRepository.save(tpl);
        auditService.log(MODULE, "SET_DEFAULT_TEMPLATE", "invoiceTemplate#" + id,
                "Default invoice template: " + tpl.getName());
    }

    // ----- Email templates -----

    @Transactional(readOnly = true)
    public Page<EmailTemplate> listEmailTemplates(Pageable pageable) {
        return emailTemplateRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public EmailTemplate getEmailTemplate(Long id) {
        return emailTemplateRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean emailTemplateCodeExists(String code, Long id) {
        return id == null
                ? emailTemplateRepository.existsByCodeIgnoreCase(code)
                : emailTemplateRepository.existsByCodeIgnoreCaseAndIdNot(code, id);
    }

    @Transactional
    public EmailTemplate saveEmailTemplate(EmailTemplateForm form, Long id) {
        EmailTemplate tpl = id == null ? new EmailTemplate() : emailTemplateRepository.findById(id).orElseThrow();
        tpl.setCode(form.code().trim().toLowerCase(Locale.ROOT));
        tpl.setName(form.name().trim());
        tpl.setSubject(form.subject().trim());
        tpl.setContentType(trimToNull(form.contentType()));
        tpl.setBody(form.body());
        tpl.setActive(form.active());
        EmailTemplate saved = emailTemplateRepository.save(tpl);
        auditService.log(MODULE, id == null ? "CREATE_TEMPLATE" : "UPDATE_TEMPLATE",
                "emailTemplate#" + saved.getId(), "Email template: " + saved.getCode());
        return saved;
    }

    // ----- Notifications -----

    @Transactional(readOnly = true)
    public Page<NotificationPreference> listNotifications(Pageable pageable) {
        return notificationPreferenceRepository.findAll(pageable);
    }

    @Transactional
    public void updateNotificationPreference(String moduleCode, Boolean emailEnabled,
                                             Boolean inAppEnabled, Boolean smsEnabled, Boolean active) {
        NotificationPreference pref = notificationPreferenceRepository.findByModuleCode(moduleCode).orElse(null);
        if (pref == null) {
            return;
        }
        pref.setEmailEnabled(Boolean.TRUE.equals(emailEnabled));
        pref.setInAppEnabled(Boolean.TRUE.equals(inAppEnabled));
        pref.setSmsEnabled(Boolean.TRUE.equals(smsEnabled));
        pref.setActive(Boolean.TRUE.equals(active));
        notificationPreferenceRepository.save(pref);
        auditService.log(MODULE, "UPDATE_NOTIFICATIONS", "notifications#" + moduleCode,
                pref.getModuleName() + " preferences updated");
    }

    // ----- Security -----

    @Transactional(readOnly = true)
    public SecuritySettings getSecuritySettings() {
        return securitySettingsRepository.findById(1L).orElseGet(() -> {
            SecuritySettings s = SecuritySettings.builder().id(1L).build();
            return securitySettingsRepository.save(s);
        });
    }

    @Transactional
    public SecuritySettings saveSecuritySettings(SecuritySettingsForm form) {
        SecuritySettings settings = getSecuritySettings();
        settings.setTwoFactorRequired(form.twoFactorRequired());
        settings.setPasswordMinLength(form.passwordMinLength());
        settings.setRequireUppercase(form.requireUppercase());
        settings.setRequireNumber(form.requireNumber());
        settings.setRequireSpecialChar(form.requireSpecialChar());
        settings.setPasswordExpiryDays(form.passwordExpiryDays());
        settings.setMaxLoginAttempts(form.maxLoginAttempts());
        settings.setLockoutMinutes(form.lockoutMinutes());
        settings.setSessionTimeoutMinutes(form.sessionTimeoutMinutes());
        settings.setAllowPublicSignup(form.allowPublicSignup());
        settings.setDefaultRole(form.defaultRole().trim());
        settings.setIpWhitelistEnabled(form.ipWhitelistEnabled());
        SecuritySettings saved = securitySettingsRepository.save(settings);
        auditService.log(MODULE, "UPDATE_SECURITY", "security#1", "Security settings updated");
        return saved;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}