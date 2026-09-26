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
import com.ntaganira.heritier.ibook.enums.AccountType;
import com.ntaganira.heritier.ibook.enums.JournalEntryStatus;
import com.ntaganira.heritier.ibook.enums.JournalEntryType;
import com.ntaganira.heritier.ibook.enums.PeriodStatus;
import com.ntaganira.heritier.ibook.enums.TaxTreatment;
import com.ntaganira.heritier.ibook.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
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
                                       AccountRepository accountRepository,
                                       JournalEntryRepository journalEntryRepository,
                                       AccountingPeriodRepository accountingPeriodRepository,
                                       CustomerRepository customerRepository,
                                       VendorRepository vendorRepository,
                                       TaxRateRepository taxRateRepository,
                                       ProductCategoryRepository categoryRepository,
                                       WarehouseRepository warehouseRepository,
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
            seedAccounts(accountRepository);
            backfillRequiredAccounts(accountRepository);
            backfillRequiredSequences(numberingSequenceRepository);
            seedJournalEntries(accountRepository, journalEntryRepository);
            seedCustomers(customerRepository);
            seedVendors(vendorRepository);
            seedTaxRates(taxRateRepository);
            seedInventoryBasics(categoryRepository, warehouseRepository);
            seedAccountingPeriods(accountingPeriodRepository, companyRepository);
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

    private void seedInventoryBasics(ProductCategoryRepository categoryRepository,
                                     WarehouseRepository warehouseRepository) {
        if (categoryRepository.count() == 0) {
            categoryRepository.save(ProductCategory.builder().code("GEN").name("General")
                    .description("Uncategorised items").active(true).build());
            categoryRepository.save(ProductCategory.builder().code("SVC").name("Services")
                    .description("Billable services").active(true).build());
        }
        if (warehouseRepository.count() == 0) {
            warehouseRepository.save(Warehouse.builder().code("MAIN").name("Main store")
                    .location("Kigali").defaultLocation(true).active(true).build());
        }
    }

    private void seedTaxRates(TaxRateRepository repository) {
        if (repository.count() > 0) {
            return;
        }
        repository.save(TaxRate.builder().code("VAT18").name("Standard VAT")
                .rate(new BigDecimal("18.00")).treatment(TaxTreatment.STANDARD)
                .description("Standard Rwanda VAT rate").defaultRate(true).active(true).build());
        repository.save(TaxRate.builder().code("VAT0").name("Zero-rated")
                .rate(BigDecimal.ZERO).treatment(TaxTreatment.ZERO_RATED)
                .description("Exports and other zero-rated supplies").defaultRate(false).active(true).build());
        repository.save(TaxRate.builder().code("EXEMPT").name("Exempt")
                .rate(BigDecimal.ZERO).treatment(TaxTreatment.EXEMPT)
                .description("Supplies outside the scope of VAT").defaultRate(false).active(true).build());
    }

    private void seedVendors(VendorRepository vendorRepository) {
        if (vendorRepository.count() > 0) {
            return;
        }
        vendor(vendorRepository, "Kigali Office Supplies Ltd", "sales@kigalioffice.rw", "+250 788 310 440", "Kigali", "201234567", "Net 30");
        vendor(vendorRepository, "Rwanda Energy Group", "billing@reg.rw", "+250 788 111 222", "Kigali", "202345678", "Due on receipt");
        vendor(vendorRepository, "Nyabugogo Transporters", "ops@nyabugogotrans.rw", "+250 782 664 010", "Kigali", "203456789", "Net 15");
        vendor(vendorRepository, "Highland Coffee Traders", "accounts@highlandcoffee.rw", "+250 789 220 551", "Huye", "204567890", "Net 30");
        vendor(vendorRepository, "Akagera Hardware", "info@akagerahardware.rw", "+250 733 880 114", "Rwamagana", "205678901", "Net 60");
        vendor(vendorRepository, "Lake Kivu Logistics", "finance@kivulogistics.rw", "+250 788 445 909", "Karongi", "206789012", "Net 30");
    }

    private void vendor(VendorRepository vendorRepository, String name, String email, String phone,
                        String city, String taxId, String paymentTerms) {
        vendorRepository.save(Vendor.builder()
                .name(name)
                .companyName(name)
                .email(email)
                .phone(phone)
                .city(city)
                .country("Rwanda")
                .taxId(taxId)
                .paymentTerms(paymentTerms)
                .openingBalance(BigDecimal.ZERO)
                .active(true)
                .build());
    }

    private void seedCustomers(CustomerRepository customerRepository) {
        if (customerRepository.count() > 0) {
            return;
        }
        customer(customerRepository, "ABC Construction Ltd", "info@abcconstruction.rw", "+250 788 401 220", "Kigali", "101234568", "4850000", true);
        customer(customerRepository, "Gorilla Retreat Ltd", "billing@gorillaretreat.rw", "+250 782 340 118", "Musanze", "101987654", "0", true);
        customer(customerRepository, "Kivu Fresh Ltd", "sales@kivufresh.rw", "+250 788 190 774", "Karongi", "102345679", "540000", true);
        customer(customerRepository, "Rwanda Artisan Co-op", "admin@rwandaartisan.biz", "+250 733 555 098", "Nyagatare", "103456780", "1275000", true);
        customer(customerRepository, "Chantal Designs", "hello@chantaldesigns.rw", "+250 789 620 443", "Kigali", "104567891", "890000", true);
        customer(customerRepository, "Mountance Logistics", "contact@mountance.co", "+250 722 081 336", "Huye", "105678902", "3450000", false);
        customer(customerRepository, "Zanazi Suppliers", "orders@zanazi.rw", "+250 788 900 112", "Kigali", "106789013", "0", true);
        customer(customerRepository, "Rwanda Fresh Farms", "ops@rwandafresh.rw", "+250 783 000 445", "Rwamagana", "107890124", "210000", true);
        customer(customerRepository, "Volcano Tours", "bookings@volcanotours.rw", "+250 782 550 998", "Kinigi", "108901235", "675000", true);
        customer(customerRepository, "Kigali Tech Hub", "admin@kigalitech.rw", "+250 730 110 220", "Kigali", "109012346", "0", true);
        for (int i = 1; i <= 35; i++) {
            customer(customerRepository, "Sample Customer " + i, "customer" + i + "@example.rw",
                    "+250 788 000 " + String.format("%02d", i), "Kigali", "10000" + i, "0", true);
        }
    }

    private void customer(CustomerRepository customerRepository, String name, String email, String phone,
                          String city, String taxId, String openingBalance, boolean active) {
        customerRepository.save(Customer.builder()
                .name(name)
                .companyName(name)
                .email(email)
                .phone(phone)
                .city(city)
                .country("Rwanda")
                .taxId(taxId)
                .openingBalance(new BigDecimal(openingBalance))
                .active(active)
                .build());
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
        seq(repository, "Estimates", "ESTIMATE", "EST-", null, 4, 1, true);
        seq(repository, "Sales Orders", "SALE_ORDER", "SO-", null, 4, 1, true);
        seq(repository, "Bills", "BILL", "BILL-", null, 4, 1, true);
        seq(repository, "Purchase Orders", "PURCHASE_ORDER", "PO-", null, 4, 1, true);
        seq(repository, "Credit Notes", "CREDIT_NOTE", "CN-", null, 4, 1, true);
        seq(repository, "Expenses", "EXPENSE", "EXP-", null, 4, 1, true);
        seq(repository, "Stock Transfers", "TRANSFER", "TRF-", null, 4, 1, true);
        seq(repository, "Stock Counts", "STOCK_COUNT", "SC-", null, 4, 1, true);
        seq(repository, "Fixed Assets", "FIXED_ASSET", "FA-", null, 4, 1, false);
        seq(repository, "Depreciation Runs", "DEPRECIATION", "DEP-", null, 4, 1, false);
        seq(repository, "Asset Transfers", "ASSET_TRANSFER", "ATR-", null, 4, 1, false);
        seq(repository, "Asset Disposals", "DISPOSAL", "DIS-", null, 4, 1, false);
        seq(repository, "Projects", "PROJECT", "PRJ-", null, 4, 1, false);
        seq(repository, "Employees", "EMPLOYEE", "EMP-", null, 4, 1, false);
        seq(repository, "Payroll Runs", "PAYROLL", "PAY-", null, 4, 1, false);
        seq(repository, "Remittances", "REMITTANCE", "REM-", null, 4, 1, false);
        seq(repository, "Withholding Certificates", "WITHHOLDING", "WHT-", null, 4, 1, false);
        seq(repository, "Payment Requests", "PAYREQUEST", "PRQ-", null, 4, 1, false);
        seq(repository, "Journal Entries", "JOURNAL", "JE-2026-", null, 5, 8, true);
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

    /**
     * Adds the handful of accounts the code posts to by name, where an existing database is missing
     * them.
     *
     * <p>{@link #seedAccounts} stops as soon as the chart has anything in it, so every account added
     * to the seed after the first install has never reached a live database. Six of the recorded
     * issues were that, and it is not a cosmetic problem: {@code BillService} and {@code
     * InvoiceService} fall back when 1402 or 5200 is absent, and a fallback posts VAT and cost of
     * sales to the wrong account rather than failing, so the numbers come out wrong quietly.
     *
     * <p><strong>Only these accounts are backfilled, not the whole chart.</strong> Re-running the full
     * seed against a live database would resurrect accounts somebody had deliberately deleted and
     * rename ones they had renamed. The list here is exactly the codes the application reaches for by
     * literal string; anything else is the bookkeeper's business, not the seeder's.
     *
     * <p><strong>Every backfilled account opens at zero</strong>, whatever the seed says. The seed
     * gives 1301 an opening balance of 12,000,000 because a fresh demo needs stock to sell; adding
     * that figure to a live chart would invent twelve million of inventory that no journal entry
     * supports, and the balance sheet would be wrong by exactly that much.
     */
    private void backfillRequiredAccounts(AccountRepository repository) {
        ensureAccount(repository, "1201", "Accounts receivable", AccountType.ASSET);
        ensureAccount(repository, "1301", "Inventory — goods for resale", AccountType.ASSET);
        ensureAccount(repository, "1402", "VAT receivable (input)", AccountType.ASSET);
        ensureAccount(repository, "2001", "Accounts payable", AccountType.LIABILITY);
        ensureAccount(repository, "2101", "VAT payable", AccountType.LIABILITY);
        ensureAccount(repository, "2103", "Net pay payable", AccountType.LIABILITY);
        ensureAccount(repository, "2104", "RSSB contributions payable", AccountType.LIABILITY);
        ensureAccount(repository, "2105", "CBHI payable", AccountType.LIABILITY);
        ensureAccount(repository, "2106", "Withholding tax payable", AccountType.LIABILITY);
        ensureAccount(repository, "2107", "Excise duty payable", AccountType.LIABILITY);
        ensureAccount(repository, "4004", "Gain on asset disposal", AccountType.REVENUE);
        ensureAccount(repository, "5007", "Loss on asset disposal", AccountType.EXPENSE);
        ensureAccount(repository, "5008", "Employer social contributions", AccountType.EXPENSE);
        ensureAccount(repository, "5200", "Cost of goods sold", AccountType.EXPENSE);
    }

    /** Creates one account if nothing already holds that code. Always opens at zero. */
    private void ensureAccount(AccountRepository repository, String code, String name, AccountType type) {
        if (repository.findByCodeIgnoreCase(code).isPresent()) {
            return;
        }
        repository.save(Account.builder().code(code).name(name).type(type).parentId(null)
                .openingBalance(BigDecimal.ZERO).active(true).build());
        log.info("Backfilled missing account {} {}", code, name);
    }

    /**
     * Adds any numbering sequence an existing database is missing.
     *
     * <p>Same cause as the accounts, and the same five recorded issues: {@link #seedNumbering} stops
     * once one sequence exists, so ESTIMATE, PROJECT, STOCK_COUNT, TRANSFER, ASSET_TRANSFER and
     * PAYREQUEST never arrived. Every service falls back to a random number when its sequence is
     * absent, which is why documents come out as {@code EST-2026-11501} instead of {@code EST-0001}.
     *
     * <p>Unlike the chart of accounts, backfilling all of these is safe. A missing sequence has no
     * legitimate reason to be missing — nobody deletes one on purpose — and adding it changes only
     * how the next document is numbered, never anything already recorded.
     */
    private void backfillRequiredSequences(NumberingSequenceRepository repository) {
        ensureSeq(repository, "Invoices", "INVOICE", "INV-", 4, true);
        ensureSeq(repository, "Estimates", "ESTIMATE", "EST-", 4, true);
        ensureSeq(repository, "Sales Orders", "SALE_ORDER", "SO-", 4, true);
        ensureSeq(repository, "Bills", "BILL", "BILL-", 4, true);
        ensureSeq(repository, "Credit Notes", "CREDIT_NOTE", "CN-", 4, true);
        ensureSeq(repository, "Projects", "PROJECT", "PRJ-", 4, false);
        ensureSeq(repository, "Employees", "EMPLOYEE", "EMP-", 4, false);
        ensureSeq(repository, "Payroll Runs", "PAYROLL", "PAY-", 4, false);
        ensureSeq(repository, "Remittances", "REMITTANCE", "REM-", 4, false);
        ensureSeq(repository, "Withholding Certificates", "WITHHOLDING", "WHT-", 4, false);
        ensureSeq(repository, "Payment Requests", "PAYREQUEST", "PRQ-", 4, false);
        ensureSeq(repository, "Stock Counts", "STOCK_COUNT", "SC-", 4, false);
        ensureSeq(repository, "Stock Transfers", "TRANSFER", "TRF-", 4, false);
        ensureSeq(repository, "Asset Transfers", "ASSET_TRANSFER", "ATR-", 4, false);
    }

    /**
     * Creates one sequence if that document type has none.
     *
     * <p>It starts at 1 rather than guessing where the existing documents got to. Where a module has
     * been issuing fallback numbers, the next document becomes {@code PRQ-0001} while older ones keep
     * their random numbers — untidy, but every number stays unique and nothing already issued moves.
     * Picking a higher start would mean parsing random fallbacks to find a maximum, which is guesswork
     * that could collide.
     */
    private void ensureSeq(NumberingSequenceRepository repository, String name, String docType,
                           String prefix, int padding, boolean resetYearly) {
        if (repository.findByDocType(docType).isPresent()) {
            return;
        }
        repository.save(NumberingSequence.builder().name(name).docType(docType).prefix(prefix)
                .suffix(null).padding(padding).nextNumber(1).resetYearly(resetYearly).active(true).build());
        log.info("Backfilled missing numbering sequence {} ({})", docType, prefix);
    }

    private void seedAccounts(AccountRepository repository) {
        if (repository.count() > 0) {
            return;
        }
        account(repository, "1000", "Bank accounts", AccountType.ASSET, null, "0", true);
        account(repository, "1101", "BK Bank — Current", AccountType.ASSET, "1000", "5000000", true);
        account(repository, "1102", "COGEBANQUE — Savings", AccountType.ASSET, "1000", "2400000", true);
        account(repository, "1105", "MTN Mobile Money", AccountType.ASSET, "1000", "800000", true);
        account(repository, "1201", "Accounts receivable", AccountType.ASSET, null, "0", true);
        account(repository, "1301", "Inventory — goods for resale", AccountType.ASSET, null, "12000000", true);
        account(repository, "1401", "Prepaid expenses", AccountType.ASSET, null, "500000", true);
        account(repository, "1402", "VAT receivable (input)", AccountType.ASSET, null, "0", true);
        account(repository, "1501", "Office equipment", AccountType.ASSET, null, "6500000", true);
        account(repository, "1509", "Accumulated depreciation — office equipment", AccountType.ASSET, "1501", "-1100000", true);
        account(repository, "2000", "Current liabilities", AccountType.LIABILITY, null, "0", true);
        account(repository, "2001", "Accounts payable", AccountType.LIABILITY, null, "0", true);
        account(repository, "2002", "Accrued expenses", AccountType.LIABILITY, null, "750000", true);
        account(repository, "2101", "VAT payable", AccountType.LIABILITY, null, "0", true);
        account(repository, "2102", "PAYE payable", AccountType.LIABILITY, null, "0", true);
        account(repository, "2103", "Net pay payable", AccountType.LIABILITY, null, "0", true);
        account(repository, "2104", "RSSB contributions payable", AccountType.LIABILITY, null, "0", true);
        account(repository, "2105", "CBHI payable", AccountType.LIABILITY, null, "0", true);
        account(repository, "2106", "Withholding tax payable", AccountType.LIABILITY, null, "0", true);
        account(repository, "2107", "Excise duty payable", AccountType.LIABILITY, null, "0", true);
        account(repository, "2301", "Short-term loans", AccountType.LIABILITY, null, "4000000", true);
        account(repository, "2401", "Long-term loan — bank", AccountType.LIABILITY, null, "8000000", true);
        account(repository, "3000", "Owner's equity", AccountType.EQUITY, null, "0", true);
        account(repository, "3001", "Owner's equity — share capital", AccountType.EQUITY, null, "3000000", true);
        account(repository, "3005", "Owner's drawings", AccountType.EQUITY, null, "0", true);
        account(repository, "3100", "Retained earnings", AccountType.EQUITY, null, "-350000", true);
        account(repository, "4000", "Income", AccountType.REVENUE, null, "0", true);
        account(repository, "4001", "Consulting services", AccountType.REVENUE, null, "0", true);
        account(repository, "4002", "Product sales", AccountType.REVENUE, null, "0", true);
        account(repository, "4003", "Interest income", AccountType.REVENUE, null, "0", true);
        account(repository, "4004", "Gain on asset disposal", AccountType.REVENUE, null, "0", true);
        account(repository, "5000", "Operating expenses", AccountType.EXPENSE, null, "0", true);
        account(repository, "5001", "Rent expense", AccountType.EXPENSE, null, "0", true);
        account(repository, "5002", "Salaries & wages", AccountType.EXPENSE, null, "0", true);
        account(repository, "5003", "Utilities", AccountType.EXPENSE, null, "0", true);
        account(repository, "5004", "Travel & transport", AccountType.EXPENSE, null, "0", true);
        account(repository, "5005", "Office supplies", AccountType.EXPENSE, null, "0", true);
        account(repository, "5006", "Depreciation expense", AccountType.EXPENSE, null, "0", true);
        account(repository, "5007", "Loss on asset disposal", AccountType.EXPENSE, null, "0", true);
        account(repository, "5008", "Employer social contributions", AccountType.EXPENSE, null, "0", true);
        account(repository, "5200", "Cost of goods sold", AccountType.EXPENSE, null, "0", true);
        account(repository, "5101", "Bank charges", AccountType.EXPENSE, null, "0", true);
    }

    private void account(AccountRepository repository, String code, String name, AccountType type,
                         String parentCode, String opening, boolean active) {
        Long parentId = parentCode == null || parentCode.isBlank()
                ? null
                : repository.findByCodeIgnoreCase(parentCode).map(Account::getId).orElse(null);
        repository.save(Account.builder().code(code).name(name).type(type).parentId(parentId)
                .openingBalance(new BigDecimal(opening)).active(active).build());
    }

    private void seedJournalEntries(AccountRepository accountRepository,
                                    JournalEntryRepository journalEntryRepository) {
        if (journalEntryRepository.count() > 0) {
            return;
        }
        journalEntry(journalEntryRepository, accountRepository, "JE-2026-00001", "2026-07-31", "RENT-2026-07",
                "July rent accrual", JournalEntryStatus.POSTED, Map.of("5001", "900000", "2002", "-900000"));
        journalEntry(journalEntryRepository, accountRepository, "JE-2026-00002", "2026-08-05", "CAP-2026-08",
                "Owner capital — cash top-up", JournalEntryStatus.POSTED, Map.of("1101", "500000", "3001", "-500000"));
        journalEntry(journalEntryRepository, accountRepository, "JE-2026-00003", "2026-08-15", "INV-2026-0014",
                "Consulting income — project KW-14", JournalEntryStatus.POSTED, Map.of("1102", "2400000", "4001", "-2400000"));
        journalEntry(journalEntryRepository, accountRepository, "JE-2026-00004", "2026-08-20", "EXP-2026-0021",
                "Field visit transport reimbursement", JournalEntryStatus.POSTED, Map.of("5004", "185000", "1105", "-185000"));
        journalEntry(journalEntryRepository, accountRepository, "JE-2026-00005", "2026-09-01", "DEP-2026-08",
                "August depreciation — office equipment", JournalEntryStatus.POSTED, Map.of("5006", "110000", "1509", "-110000"));
        journalEntry(journalEntryRepository, accountRepository, "JE-2026-00006", "2026-09-05", "DRAW-2026-09",
                "Owner drawings — August", JournalEntryStatus.POSTED, Map.of("3005", "300000", "1101", "-300000"));
        journalEntry(journalEntryRepository, accountRepository, "JE-2026-00007", "2026-09-07", null,
                "Draft accrual — pending review", JournalEntryStatus.DRAFT, Map.of("5001", "75000", "2002", "-75000"));
    }

    private void journalEntry(JournalEntryRepository repository, AccountRepository accountRepository, String entryNo,
                              String date, String reference, String memo, JournalEntryStatus status,
                              Map<String, String> lines) {
        List<JournalLine> journalLines = new ArrayList<>();
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        int order = 0;
        for (Map.Entry<String, String> lineEntry : lines.entrySet()) {
            Account account = accountRepository.findByCodeIgnoreCase(lineEntry.getKey()).orElse(null);
            if (account == null) {
                continue;
            }
            BigDecimal amount = new BigDecimal(lineEntry.getValue());
            boolean debit = amount.signum() > 0;
            BigDecimal debitValue = debit ? amount.abs() : BigDecimal.ZERO;
            BigDecimal creditValue = debit ? BigDecimal.ZERO : amount.abs();
            totalDebits = totalDebits.add(debitValue);
            totalCredits = totalCredits.add(creditValue);
            journalLines.add(JournalLine.builder().accountId(account.getId()).accountCode(account.getCode())
                    .accountName(account.getName()).memo(null).debit(debitValue).credit(creditValue)
                    .sortOrder(order++).build());
        }
        JournalEntry entry = JournalEntry.builder().entryNo(entryNo).entryDate(LocalDate.parse(date))
                .type(JournalEntryType.MANUAL).status(status).reference(reference).memo(memo)
                .totalDebits(totalDebits).totalCredits(totalCredits).createdBy("admin@ebookonline.rw").build();
        for (JournalLine line : journalLines) {
            entry.addLine(line);
        }
        repository.save(entry);
    }

    private void seedAccountingPeriods(AccountingPeriodRepository repository, CompanyRepository companyRepository) {
        if (repository.count() > 0) {
            return;
        }
        String fiscalYearStart = companyRepository.findFirstByOrderByIdAsc()
                .map(Company::getFiscalYearStart).orElse("July");
        int startMonth = fiscalYearStartMonth(fiscalYearStart);
        LocalDate today = LocalDate.now();
        int fiscalYear = today.getMonthValue() >= startMonth ? today.getYear() : today.getYear() - 1;
        int created = 0;
        for (int m = 0; m < 12; m++) {
            int monthIndex = ((startMonth - 1) + m) % 12 + 1;
            int year = fiscalYear + ((startMonth - 1) + m) / 12;
            YearMonth yearMonth = YearMonth.of(year, monthIndex);
            LocalDate start = yearMonth.atDay(1);
            LocalDate end = yearMonth.atEndOfMonth();
            String code = "FY" + fiscalYear + "-M" + String.format("%02d", m + 1);
            String label = capitalize(yearMonth.getMonth().name().toLowerCase(java.util.Locale.ENGLISH)) + " " + year;
            repository.save(AccountingPeriod.builder().code(code).label(label).fiscalYear(fiscalYear)
                    .startDate(start).endDate(end)
                    .status(end.isBefore(today) ? PeriodStatus.CLOSED : PeriodStatus.OPEN).build());
            created++;
        }
    }

    private static int fiscalYearStartMonth(String fiscalYearStart) {
        if (fiscalYearStart == null || fiscalYearStart.isBlank()) {
            return 1;
        }
        String[] months = {"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};
        for (int i = 0; i < months.length; i++) {
            if (months[i].equalsIgnoreCase(fiscalYearStart.trim())) {
                return i + 1;
            }
        }
        return 1;
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}