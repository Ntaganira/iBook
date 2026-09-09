/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.config
 * - File      : DataSeeder.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : Startup seeding for roles, admin user and system settings
 * </pre>
 */
package com.ntaganira.heritier.ibook.config;

import com.ntaganira.heritier.ibook.entity.*;
import com.ntaganira.heritier.ibook.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seedInitialData(RoleRepository roleRepository,
                                      UserRepository userRepository,
                                      PermissionRepository permissionRepository,
                                      CompanyRepository companyRepository,
                                      BranchRepository branchRepository,
                                      CurrencyRepository currencyRepository,
                                      ExchangeRateRepository exchangeRateRepository,
                                      NumberingSequenceRepository numberingSequenceRepository,
                                      ApprovalWorkflowRepository approvalWorkflowRepository,
                                      InvoiceTemplateRepository invoiceTemplateRepository,
                                      EmailTemplateRepository emailTemplateRepository,
                                      NotificationPreferenceRepository notificationPreferenceRepository,
                                      SecuritySettingsRepository securitySettingsRepository,
                                      AuditLogRepository auditLogRepository,
                                      PasswordEncoder passwordEncoder) {
        return args -> {
            seedRolesAndPermissions(roleRepository, permissionRepository);

            if (!userRepository.existsByEmail("admin@ebookonline.rw")) {
                Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
                User admin = User.builder()
                        .firstName("Heritier")
                        .lastName("NTAGANIRA")
                        .email("admin@ebookonline.rw")
                        .username("admin@ebookonline.rw")
                        .password(passwordEncoder.encode("Admin#2026"))
                        .phone("+250788533669")
                        .enabled(true)
                        .emailVerified(true)
                        .roles(new HashSet<>(Set.of(adminRole)))
                        .build();
                userRepository.save(admin);
            }

            seedCompany(companyRepository);
            seedBranches(branchRepository);
            seedCurrencies(currencyRepository);
            seedExchangeRates(exchangeRateRepository);
            seedNumbering(numberingSequenceRepository);
            seedWorkflows(approvalWorkflowRepository);
            seedInvoiceTemplates(invoiceTemplateRepository);
            seedEmailTemplates(emailTemplateRepository);
            seedNotificationPreferences(notificationPreferenceRepository);
            seedSecurity(securitySettingsRepository);
            seedAuditSamples(auditLogRepository);
        };
    }

    private void seedRolesAndPermissions(RoleRepository roleRepository, PermissionRepository permissionRepository) {
        Map<String, String> defs = permissionDefs();
        permissionRepository.findAll().forEach(p -> {
            if (!defs.containsKey(p.getCode())) {
                defs.put(p.getCode(), p.getDescription());
            }
        });
        List<Permission> all = defs.entrySet().stream()
                .map(e -> permissionRepository.findByCodeIgnoreCase(e.getKey()).orElseGet(() ->
                        permissionRepository.save(Permission.builder().code(e.getKey()).description(e.getValue()).build())))
                .toList();

        Role admin = roleRepository.findByName("ADMIN").orElseGet(() ->
                roleRepository.save(Role.builder().name("ADMIN").description("Administrator").build()));
        if (admin.getPermissions().isEmpty()) {
            admin.setPermissions(new HashSet<>(all));
            roleRepository.save(admin);
        }
        roleRepository.findByName("USER").orElseGet(() ->
                roleRepository.save(Role.builder().name("USER")
                        .description("Standard user")
                        .permissions(new HashSet<>(all.stream()
                                .filter(p -> p.getCode().equals("dashboard.view")
                                        || p.getCode().equals("sales.view")
                                        || p.getCode().equals("purchases.view")
                                        || p.getCode().equals("inventory.view")
                                        || p.getCode().equals("banking.view")
                                        || p.getCode().equals("accounting.view")
                                        || p.getCode().equals("reports.view")
                                        || p.getCode().equals("reports.export"))
                                .toList()))
                        .build()));
    }

    private Map<String, String> permissionDefs() {
        return Map.ofEntries(
                Map.entry("dashboard.view", "View the dashboard"),
                Map.entry("settings.view", "Access system settings"),
                Map.entry("settings.company", "View company profile"),
                Map.entry("settings.company.edit", "Edit company profile"),
                Map.entry("settings.branches", "View branches"),
                Map.entry("settings.branches.edit", "Create and edit branches"),
                Map.entry("settings.users", "View users"),
                Map.entry("settings.users.edit", "Create and edit users"),
                Map.entry("settings.roles", "View roles"),
                Map.entry("settings.roles.edit", "Create and edit roles"),
                Map.entry("settings.permissions", "View permissions"),
                Map.entry("settings.workflows.edit", "Manage approval workflows"),
                Map.entry("settings.currencies", "View currencies"),
                Map.entry("settings.currencies.edit", "Create and edit currencies"),
                Map.entry("settings.rates", "View exchange rates"),
                Map.entry("settings.rates.edit", "Add exchange rates"),
                Map.entry("settings.fiscal-year", "View fiscal year settings"),
                Map.entry("settings.fiscal-year.edit", "Edit fiscal year settings"),
                Map.entry("settings.templates", "Manage document templates"),
                Map.entry("settings.numbering", "View numbering sequences"),
                Map.entry("settings.numbering.edit", "Edit numbering sequences"),
                Map.entry("settings.notifications", "Manage notification preferences"),
                Map.entry("settings.security", "Manage security settings"),
                Map.entry("settings.audit", "View the audit trail"),
                Map.entry("sales.view", "Access sales and revenue"),
                Map.entry("sales.customers", "View customers"),
                Map.entry("sales.customers.edit", "Create and edit customers"),
                Map.entry("sales.invoices", "View invoices"),
                Map.entry("sales.invoices.edit", "Create and edit invoices"),
                Map.entry("sales.estimates.edit", "Create and edit estimates"),
                Map.entry("sales.credit-notes.edit", "Create credit notes"),
                Map.entry("sales.payments.edit", "Record customer payments"),
                Map.entry("sales.collections", "Manage collections"),
                Map.entry("purchases.view", "Access purchases and expenses"),
                Map.entry("purchases.vendors", "View suppliers"),
                Map.entry("purchases.vendors.edit", "Create and edit suppliers"),
                Map.entry("purchases.bills", "View bills"),
                Map.entry("purchases.bills.edit", "Create and edit bills"),
                Map.entry("purchases.orders.edit", "Create and edit purchase orders"),
                Map.entry("purchases.expenses.edit", "Create and edit expenses"),
                Map.entry("purchases.payments.edit", "Record supplier payments"),
                Map.entry("purchases.withholding", "Manage withholding tax"),
                Map.entry("inventory.view", "Access products and inventory"),
                Map.entry("inventory.products", "View products"),
                Map.entry("inventory.products.edit", "Create and edit products"),
                Map.entry("inventory.adjustments.edit", "Record stock adjustments"),
                Map.entry("inventory.counts.edit", "Perform stock counts"),
                Map.entry("accounting.view", "Access accounting"),
                Map.entry("accounting.accounts", "View chart of accounts"),
                Map.entry("accounting.accounts.edit", "Create and edit accounts"),
                Map.entry("accounting.journals", "View journal entries"),
                Map.entry("accounting.journals.edit", "Create and edit journal entries"),
                Map.entry("accounting.periods.edit", "Manage accounting periods"),
                Map.entry("accounting.fiscal-close", "Perform fiscal year closing"),
                Map.entry("banking.view", "Access banking and cash"),
                Map.entry("banking.transactions", "View bank transactions"),
                Map.entry("banking.reconcile", "Reconcile bank accounts"),
                Map.entry("banking.uncategorized", "Categorize transactions"),
                Map.entry("assets.view", "Access fixed assets"),
                Map.entry("projects.view", "Access projects and time"),
                Map.entry("projects.timesheets", "Manage timesheets"),
                Map.entry("projects.billable", "Manage billable time"),
                Map.entry("payroll.view", "Access payroll and HR"),
                Map.entry("payroll.runs.edit", "Run payroll"),
                Map.entry("taxes.view", "Access taxes and compliance"),
                Map.entry("taxes.filings", "Prepare tax filings"),
                Map.entry("budgets.view", "Access planning and budgeting"),
                Map.entry("reports.view", "View reports"),
                Map.entry("reports.export", "Export reports"),
                Map.entry("documents.view", "Access document center"),
                Map.entry("documents.upload", "Upload documents"),
                Map.entry("integrations.view", "Access apps and integrations"));
    }

    private void seedCompany(CompanyRepository companyRepository) {
        if (companyRepository.count() > 0) {
            return;
        }
        companyRepository.save(Company.builder()
                .name("Kigali Fresh Trading Ltd")
                .legalName("Kigali Fresh Trading Ltd")
                .tin("101234567")
                .email("accounts@kigali-fresh.rw")
                .phone("+250 788 301 010")
                .website("https://kigali-fresh.rw")
                .address("KG 7 Ave, Plot 12, Kimihurura")
                .city("Kigali")
                .country("Rwanda")
                .currencyCode("RWF")
                .fiscalYearStart("July")
                .build());
    }

    private void seedBranches(BranchRepository branchRepository) {
        if (branchRepository.count() > 0) {
            return;
        }
        branchRepository.save(Branch.builder().name("Kigali HQ").code("KGL").city("Kigali")
                .country("Rwanda").contactPerson("Aline Uwase").phone("+250788301010")
                .email("kigali@kigali-fresh.rw").defaultBranch(true).active(true).build());
        branchRepository.save(Branch.builder().name("Huye Branch").code("HUY").city("Huye")
                .country("Rwanda").contactPerson("Jean Mugisha").phone("+250722900505")
                .email("huye@kigali-fresh.rw").defaultBranch(false).active(true).build());
    }

    private void seedCurrencies(CurrencyRepository currencyRepository) {
        if (currencyRepository.count() > 0) {
            return;
        }
        currencyRepository.save(Currency.builder().code("RWF").name("Rwandan Franc").symbol("RWF")
                .decimals(0).exchangeRateToBase(BigDecimal.ONE).base(true).enabled(true).build());
        seed(currencyRepository, "USD", "US Dollar", "$", 2, "1450");
        seed(currencyRepository, "EUR", "Euro", "€", 2, "1600");
        seed(currencyRepository, "KES", "Kenyan Shilling", "KES", 2, "11.50");
        seed(currencyRepository, "UGX", "Ugandan Shilling", "UGX", 0, "0.39");
        seed(currencyRepository, "TZS", "Tanzanian Shilling", "TZS", 0, "0.55");
        seed(currencyRepository, "BIF", "Burundian Franc", "BIF", 0, "0.52");
        seed(currencyRepository, "CDF", "Congolese Franc", "CDF", 2, "0.55");
    }

    private void seed(CurrencyRepository repository, String code, String name, String symbol,
                      int decimals, String rate) {
        repository.save(Currency.builder().code(code).name(name).symbol(symbol)
                .decimals(decimals).exchangeRateToBase(new BigDecimal(rate)).base(false).enabled(true).build());
    }

    private void seedExchangeRates(ExchangeRateRepository exchangeRateRepository) {
        if (exchangeRateRepository.count() > 0) {
            return;
        }
        rate(exchangeRateRepository, "RWF", "USD", "1450");
        rate(exchangeRateRepository, "RWF", "EUR", "1600");
        rate(exchangeRateRepository, "RWF", "KES", "11.50");
        rate(exchangeRateRepository, "RWF", "UGX", "0.39");
        rate(exchangeRateRepository, "RWF", "TZS", "0.55");
        rate(exchangeRateRepository, "RWF", "BIF", "0.52");
        rate(exchangeRateRepository, "RWF", "CDF", "0.55");
    }

    private void rate(ExchangeRateRepository repository, String base, String quote, String rate) {
        repository.save(ExchangeRate.builder().baseCurrency(base).quoteCurrency(quote)
                .rate(new BigDecimal(rate)).effectiveDate(LocalDate.now()).build());
    }

    private void seedNumbering(NumberingSequenceRepository repository) {
        if (repository.count() > 0) {
            return;
        }
        seq(repository, "Invoices", "INVOICE", "INV-", null, 4, 1, true);
        seq(repository, "Sales Orders", "SALE_ORDER", "SO-", null, 4, 1, true);
        seq(repository, "Bills", "BILL", "BILL-", null, 4, 1, true);
        seq(repository, "Purchase Orders", "PURCHASE_ORDER", "PO-", null, 4, 1, true);
        seq(repository, "Credit Notes", "CREDIT_NOTE", "CN-", null, 4, 1, true);
        seq(repository, "Expenses", "EXPENSE", "EXP-", null, 4, 1, true);
        seq(repository, "Journal Entries", "JOURNAL", "JE-", null, 5, 1, true);
    }

    private void seq(NumberingSequenceRepository repository, String name, String docType,
                     String prefix, String suffix, int padding, long next, boolean resetYearly) {
        repository.save(NumberingSequence.builder().name(name).docType(docType).prefix(prefix)
                .suffix(suffix).padding(padding).nextNumber(next).resetYearly(resetYearly).active(true).build());
    }

    private void seedWorkflows(ApprovalWorkflowRepository repository) {
        if (repository.count() > 0) {
            return;
        }
        repository.save(ApprovalWorkflow.builder().name("Invoice Approval").module("Sales")
                .triggerEvent("Invoice created").steps(2).daysEach(2).active(true).build());
        repository.save(ApprovalWorkflow.builder().name("Bill Approval").module("Purchases")
                .triggerEvent("Bill created").steps(1).daysEach(2).active(true).build());
        repository.save(ApprovalWorkflow.builder().name("Expense Approval").module("Expenses")
                .triggerEvent("Expense submitted").steps(1).daysEach(1).active(true).build());
        repository.save(ApprovalWorkflow.builder().name("Purchase Order Approval").module("Purchases")
                .triggerEvent("Purchase order created").steps(2).daysEach(2).active(true).build());
    }

    private void seedInvoiceTemplates(InvoiceTemplateRepository repository) {
        if (repository.count() > 0) {
            return;
        }
        repository.save(InvoiceTemplate.builder().name("Ebook Modern").layout("modern")
                .accentColor("#166534").paperSize("A4").defaultTemplate(true)
                .showsLogo(true).showsTaxSummary(true).includesTerms(true).active(true).build());
        repository.save(InvoiceTemplate.builder().name("RRA / EBM Classic").layout("classic")
                .accentColor("#0f172a").paperSize("A4").defaultTemplate(false)
                .showsLogo(true).showsTaxSummary(true).includesTerms(true).active(true).build());
        repository.save(InvoiceTemplate.builder().name("Minimalist").layout("compact")
                .accentColor("#334155").paperSize("A5").defaultTemplate(false)
                .showsLogo(true).showsTaxSummary(false).includesTerms(false).active(true).build());
    }

    private void seedEmailTemplates(EmailTemplateRepository repository) {
        if (repository.count() > 0) {
            return;
        }
        email(repository, "invoice_sent", "Invoice sent", "Your invoice {inv_no} from {company}",
                "Dear {customer}, please find your invoice attached.", true);
        email(repository, "invoice_reminder", "Invoice reminder", "Reminder: invoice {inv_no} is due",
                "Dear {customer}, your invoice {inv_no} is now overdue.", true);
        email(repository, "payment_received", "Payment received", "Payment received for invoice {inv_no}",
                "Dear {customer}, thank you for your payment of {amount}.", true);
        email(repository, "password_reset", "Password reset", "Reset your Ebook Online password",
                "Click the link below to reset your password.", true);
        email(repository, "new_user", "New user welcome", "Welcome to Ebook Online",
                "An account has been created for you. Sign in to get started.", true);
    }

    private void email(EmailTemplateRepository repository, String code, String name, String subject,
                       String body, boolean active) {
        repository.save(EmailTemplate.builder().code(code).name(name).subject(subject)
                .contentType("html").body(body).active(active).build());
    }

    private void seedNotificationPreferences(NotificationPreferenceRepository repository) {
        if (repository.count() > 0) {
            return;
        }
        pref(repository, "invoices", "Invoices", "Created, sent, paid, overdue", true, true, true);
        pref(repository, "bills", "Bills", "Received, due, overdue", true, true, false);
        pref(repository, "payments", "Payments", "Received", true, true, false);
        pref(repository, "expenses", "Expenses", "Submitted, approved", true, true, false);
        pref(repository, "banking", "Banking", "New transactions", true, true, true);
        pref(repository, "users", "Users", "Created, updated", true, true, false);
        pref(repository, "system", "System", "Maintenance, updates", true, true, false);
    }

    private void pref(NotificationPreferenceRepository repository, String code, String name,
                      String triggers, boolean email, boolean inApp, boolean sms) {
        repository.save(NotificationPreference.builder().moduleCode(code).moduleName(name)
                .triggers(triggers).emailEnabled(email).inAppEnabled(inApp).smsEnabled(sms).active(true).build());
    }

    private void seedSecurity(SecuritySettingsRepository repository) {
        if (!repository.findById(1L).isPresent()) {
            repository.save(SecuritySettings.builder().id(1L).build());
        }
    }

    private void seedAuditSamples(AuditLogRepository repository) {
        if (repository.count() > 0) {
            return;
        }
        repository.save(AuditLog.builder().actor("system").module("settings").action("INITIAL_SETUP")
                .target("workspace#1").detail("Initial workspace configuration completed").build());
        repository.save(AuditLog.builder().actor("admin@ebookonline.rw").module("accounting")
                .action("IMPORT_CHART").target("chart#default").detail("Imported standard Rwandan chart of accounts").build());
        repository.save(AuditLog.builder().actor("admin@ebookonline.rw").module("settings").action("EMAIL_TEST")
                .target("mail#smtp").detail("SMTP test message sent successfully").build());
    }
}