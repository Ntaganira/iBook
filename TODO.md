# iBook — Build Tracker

Progress against the sidebar, which lists **115 routes**. A route counts as done when it has
a controller mapping, a template, and reads real data.

**64 / 115 mapped · 51 remaining**

How to check progress yourself:

```bash
# routes in the sidebar vs routes with a GetMapping
grep -oE 'href="@\{[^}(]*' src/main/resources/templates/layout/sidebar.html | sort -u | wc -l
grep -rhoE '@GetMapping\("[^"]*"' src/main/java --include=*Controller.java | sort -u | wc -l
```

Before trusting any change: `./mvnw -q compile` (Windows: `.\mvnw.cmd -q compile`).

---

## Done

- [x] **Auth** — login, register, forgot/reset, DB-backed users, roles, permissions, page-level access
- [x] **Chart of accounts** — CRUD, hierarchy, opening balances
- [x] **Journal entries** — draft/posted, auto-numbering, ledger, trial balance, periods, fiscal close
- [x] **Opening balances** — bulk edit with debit/credit balance check
- [x] **Customers** — CRUD, detail page, receivables stats
- [x] **Vendors** — CRUD, detail page, payables stats
- [x] **Invoices** — full lifecycle, GL posting, payments, void with reversal
- [x] **Bills** — full lifecycle, AP posting, payments, void with reversal
- [x] **Banking** — cash/bank accounts, transactions from posted ledger activity
- [x] **Reports** — P&L, balance sheet, cash flow, general ledger, trial balance
- [x] **Taxes** — rate config, VAT return, liability, transactions, filings
- [x] **Settings** — 15 pages: company, branches, currencies, numbering, templates, users, roles, audit

---

## Tier 1 — no new entities, just pages — **COMPLETE**

Reads tables that already exist. All seven done.

- [x] `/sales/aging` — per-customer buckets, drill-down, as-of date
- [x] `/purchases/aging` — per-vendor buckets, drill-down, as-of date
- [x] `/sales/payments` — period + method filter, breakdowns by method and account
- [x] `/purchases/payments` — period + method filter, breakdowns by method and account
- [x] `/sales/statements` — brought-forward balance, running balance, period filter
- [x] `/purchases/statements` — brought-forward balance, running balance, period filter
- [x] `/sales/collections` — worklist with contact details and last-payment date

---

## Tier 2 — one new entity each

### Inventory (10) — foundation built
`Product`, `ProductCategory`, `Warehouse`, `StockMovement` entities all exist.

- [x] `/inventory/products` — CRUD with on-hand quantity, reorder flagging
- [x] `/inventory/categories` — CRUD, delete blocked while in use
- [x] `/inventory/warehouses` — CRUD with a single default location
- [x] `/inventory/movements` — filterable movement log
- [x] `/inventory/adjustments` — in/out with GL posting
- [x] `/inventory/valuation` — at standard cost, as-of date
- [ ] `/inventory/brands` — currently a free-text field on Product; needs its own entity
- [ ] `/inventory/bundles` — needs `ProductBundle` + components
- [x] `/inventory/transfers` — draft/complete/cancel/void, paired `TRANSFER_OUT`/`TRANSFER_IN`
      movements at the two locations, per-location stock check before the stock moves. The move
      itself is non-posting; only stock that fails to arrive is written off from Inventory (1301)
      to Cost of sales (5200). Voiding writes mirror movements plus a reversing entry.
- [ ] `/inventory/counts` — needs `StockCount` + count lines
- [x] **Products linked to invoice and bill lines** — picker with autofill; posting now moves stock
      and applies perpetual inventory (see Known issues for the accounting change)

### Sales documents (4) — variants of `Invoice`
- [x] `/sales/estimates` — full lifecycle, converts to a draft invoice via `InvoiceService`
- [x] `/sales/credit-notes` — full lifecycle, posts the reverse of an invoice, optional stock return,
      applies the credit to a linked invoice (see Known issues for the statement gap)
- [x] `/sales/orders` — draft/confirm/cancel/reopen, expected-delivery late flag, converts to a
      draft invoice via `InvoiceService`. Non-posting: nothing reaches the ledger until that
      invoice is posted.
- [x] `/sales/recurring` — named schedules on a weekly to yearly cycle, optional end date and
      occurrence cap, swept daily by `RecurringInvoiceScheduler`. Each occurrence is raised through
      `InvoiceService` dated the day it was owed, as a draft unless the schedule opts into
      auto-posting.

### Purchase documents (3) — variants of `Bill`
- [x] `/purchases/orders` — draft/confirm/cancel/reopen, expected-delivery late flag, converts to a
      draft bill via `BillService`. Non-posting, mirroring `/sales/orders`.
- [x] `/purchases/expenses` — direct spend paid from cash or bank, posts in one entry (expense
      accounts + input VAT debited, payment account credited), void writes a reversing entry.
      Never touches accounts payable.
- [x] `/purchases/contractors` — register of engaged people and firms: contract period with
      expiry flagging, agreed rate, bank details, optional link to a supplier record that surfaces
      the bills and expenses actually paid to them. Non-posting; withholding is reference only.

### Payroll (7)
`Employee`, `PayrollRun`, `Payslip`. Needs current RRA PAYE bands plus RSSB and maternity rates.

- [ ] `/payroll/employees` · `/payroll/setup` · `/payroll/allowances` · `/payroll/deductions`
- [ ] `/payroll/runs` · `/payroll/payslips` · `/payroll/remittances`

### Fixed assets (6)
`FixedAsset`, `DepreciationEntry`. Depreciation posts to the GL — mirrors the invoice posting pattern.

- [ ] `/assets/register` · `/assets/categories` · `/assets/locations`
- [ ] `/assets/depreciation` · `/assets/disposals` · `/assets/transfers`

### Projects (7)
`Project`, `TimeEntry`. Job costing tags existing journal lines to a project.

- [ ] `/projects` · `/projects/timesheets` · `/projects/billable` · `/projects/budgets`
- [ ] `/projects/job-costing` · `/projects/profitability` · `/projects/progress-billing`

### Budgets (6)
`Budget`, `BudgetLine`. Budget-vs-actual is a join against GL data that already exists.

- [ ] `/budgets` · `/budgets/vs-actual`
- [ ] `/budgets/revenue-forecast` · `/budgets/expense-forecast` · `/budgets/cash-flow-forecast`
- [ ] `/budgets/ai-forecasts` — drop unless genuinely wanted

### Documents (5)
`Attachment`. **Needs a storage decision first: disk, S3, or database.**

- [ ] `/documents` · `/documents/upload` · `/documents/attachments` · `/documents/templates`
- [ ] `/documents/ocr` — also needs an OCR service

### Other single items
- [ ] `/accounting/recurring-journals` — `RecurringJournal` + scheduler
- [ ] `/reports/builder` — `ReportDefinition`

---

## Tier 3 — blocked on something outside the codebase

Building these without the external piece produces a page that cannot work.

- [ ] `/sales/ebm` — RRA EBM 2.x certification
- [ ] `/integrations/ebm` · `/integrations/mtn-momo` · `/integrations/airtel-money`
- [ ] `/integrations/banks` · `/integrations/payments` · `/integrations/webhooks` · `/integrations/api`
- [ ] `/sales/payment-links` — payment gateway account
- [ ] `/banking/feeds` — feed import must come first
- [ ] `/banking/reconcile` — needs statement lines and match state
- [ ] `/banking/uncategorized` — depends on feed import
- [ ] `/taxes/withholding` — withholding fields on invoice/bill lines
- [ ] `/taxes/excise` — excise fields on invoice/bill lines

---

## Known issues

- [ ] **Credit notes do not appear on customer statements.** `/sales/statements` builds its running
      balance from invoice totals and `InvoicePayment` rows only, so a credited invoice's statement
      closing balance overstates the debt by the credit. Aging, collections, outstanding totals and
      the GL are all correct — only the statement is out. Fixing it means teaching
      `InvoiceService.statementFor` about `CreditNote`.
- [ ] **Enum check constraints block new enum values on existing databases.** Hibernate wrote a
      `journal_entries_type_check` (and the same for `stock_movements.movement_type`) when those
      tables were created, and `ddl-auto: update` never widens it. Adding a `CREDIT_NOTE` journal
      type failed with SQLState 23514 on the existing database, so credit notes post as
      `ADJUSTMENT` and restocking writes `ADJUSTMENT_IN`, both identified by the credit note number
      in the entry reference. Widening these needs a Flyway migration — note that
      `baseline-on-migrate` baselines existing databases at version 1, so a first script must be
      `V2__` or later to run at all.
- [ ] **Voiding a credit note does not reverse its stock movement.** The reversing journal entry is
      written and the credit is un-applied from the invoice, but the inbound stock movement stays.
      Same gap as voiding an invoice, which also leaves its outbound movement in place.
- [ ] **Orders do not reserve stock and are not partially invoicable.** Applies to both
      `/sales/orders` and `/purchases/orders`. Confirming has no effect on stock on hand, so the
      same units can be promised twice; converting always creates one invoice or bill for the whole
      order, with no partial or repeat conversion, and no goods-received step records a delivery
      against a purchase order. Sales orders also cannot be raised from an accepted estimate —
      `EstimateService` still converts straight to an invoice.
- [ ] **Record forms with a primitive `boolean` break when the checkbox is unchecked.** An unchecked
      checkbox submits nothing, and a record's canonical constructor cannot take `null` for a
      primitive `boolean`, so Spring leaves the model attribute null and the re-render dies on
      `form.<field>`. **Confirmed on `/vendors`**: posting the vendor form without `active` returned
      500 with `EL1007E: Property or field 'name' cannot be found on null` at `vendors/form` line 51,
      and in another attempt silently re-rendered the empty form without saving. Sixteen record
      forms declare primitive booleans — `AccountForm`, `BranchForm`, `CategoryForm`,
      `CreateUserForm`, `CurrencyForm`, `CustomerForm`, `EditUserForm`, `EmailTemplateForm`,
      `InvoiceTemplateForm`, `NumberingForm`, `ProductForm`, `SecuritySettingsForm`, `TaxRateForm`,
      `VendorForm`, `WarehouseForm`, `WorkflowForm` — so the rest are worth auditing, though only
      `/vendors` has been reproduced. Note the guards already in the tree are inconsistent:
      `accounts/form.html` puts `<input type="hidden" name="active" value="false">` *before* the
      checkbox, which sends two values for one field, and the binder takes the first — that may
      force `false` even when the box is ticked. `ContractorForm` avoids all of this by declaring
      `Boolean active` and folding null to false.
- [ ] **Expenses carry no receipt attachment and cannot be rebilled.** There is nowhere to attach a
      scanned receipt (waiting on the Documents storage decision) and no billable flag to recharge
      an expense to a customer or project. Stocked products are also out of scope by design — an
      expense is consumption, so nothing is capitalised to inventory (1301) the way a bill line is.
- [ ] **Contractor withholding is recorded but never applied.** `Contractor.withholdingRate` and the
      "would have been withheld" figure on the detail page are reference only — no bill or expense
      posting deducts withholding, because that needs withholding fields on document lines
      (`/taxes/withholding`, Tier 3). Contractors are also linked to spend only through an optional
      supplier record, so an unlinked contractor shows no history, and a supplier shared by two
      contractors would show the same bills under both.
- [ ] **Every money figure in the app is missing its thousands separators.** Thymeleaf's four-argument
      `#numbers.formatDecimal(x, 1, 2, 'COMMA')` reads as *(value, minIntegerDigits, decimalDigits,
      **decimalPointType**)* — so `'COMMA'` sets the **decimal point** to a comma and asks for no
      grouping at all. `RWF 1510000.00` renders as `RWF 1510000,00`, and the `, 1, 0, 'COMMA')`
      variant renders `RWF 1510000` flat. Confirmed live against known values. Grouping needs the
      five-argument form, `#numbers.formatDecimal(x, 1, 'COMMA', 2, 'POINT')`. There are **357
      occurrences across 57 templates**; the three `/inventory/transfers` templates have been
      corrected, the rest have not. Note the grouped figures that do appear on screen (`RWF
      1,250,000` and friends) are static placeholder markup in the command-palette fragment, not
      formatted values — they are not evidence that anything works.
- [ ] **`WarehouseForm` confirmed as a second instance of the record/primitive-`boolean` bug.**
      Posting `/inventory/warehouses` with `active=true` but no `defaultLocation` silently
      re-rendered the form with a 200 and saved nothing; sending both booleans saved normally. That
      makes two of the sixteen record forms reproduced — `VendorForm` (500) and `WarehouseForm`
      (silent no-op). Both still unfixed.
- [ ] **Stock has no real location until a transfer gives it one.** `Invoice`, `Bill` and
      `CreditNote` all write their stock movements with a null `warehouseId` — none of those forms
      asks for a location. `StockTransferService.onHandByWarehouse` therefore counts every
      warehouse-less movement at the **default** warehouse, because otherwise stock bought on a bill
      would be invisible to every location and no transfer could be raised against it. Consequences:
      per-location stock is only as good as that assumption, a site with no default warehouse set
      shows nothing anywhere, and the company-wide figure on `/inventory/valuation` is unaffected
      either way. The real fix is a location picker on bill and invoice lines.
- [ ] **No `TRANSFER` numbering sequence on existing databases.** `DataSeeder.seedNumbering` now
      adds `TRF-`, but it returns early when any sequence exists, so databases predating this get a
      random fallback like `TRF-2026-11501`. Add the row at `/settings/numbering` — same class of
      drift as the missing `ESTIMATE` sequence and the missing 1402 and 5200 accounts.
- [ ] **A transfer is a single step, and stock in transit is nobody's.** There is no shipped-but-not-
      yet-received state: completing a transfer writes both movements at once. Modelling goods in
      transit properly needs a goods-in-transit account (there is no `1302`), which is a chart-of-
      accounts decision, not one to make silently. The shortfall is also recorded only as the gap
      between the paired movements plus its journal entry — there is no separate `WRITE_OFF`
      movement row, so the movement log shows `TRANSFER_OUT 10` against `TRANSFER_IN 8` rather than
      an explicit loss line.
- [ ] **Recurring invoices repeat a fixed price, and auto-posting has no guard rail.** A schedule
      stores its lines once; a price rise, a new VAT rate or a product cost change is picked up only
      by editing the schedule, and editing it affects future invoices only — invoices already raised
      keep the old figures. With `autoPost` on, the nightly sweep posts to the ledger and the VAT
      return with nobody in the loop, and the only brake is the 24-occurrence catch-up cap in
      `RecurringInvoiceService`. There is also no email step: a generated invoice is never sent, so
      somebody still has to deliver it. Stock is not checked either — a schedule billing a stocked
      product will happily drive quantity on hand negative.
- [ ] **A recurring schedule's invoices are not unwound when it is stopped.** Stopping or deleting a
      schedule leaves every invoice it raised in place, posted ones included, which is the intended
      accounting treatment but means a schedule that ran wrong must be corrected invoice by invoice
      (void each, which writes its own reversing entry).
- [ ] **Unapplied credit cannot be spent.** A credit note with no linked invoice, or one larger than
      the invoice it credits, keeps the remainder as `unappliedAmount` and shows it on the list and
      view pages, but there is no screen to apply it to a later invoice or refund it.
- [ ] **Perpetual inventory is now live.** Posting a bill line for a stocked product debits
      Inventory (1301) instead of the line's expense account; posting an invoice credits Inventory
      and debits Cost of goods sold (5200) at the product's cost price. Non-stocked lines are
      unaffected. Verify this matches your intended treatment before entering real data.
- [ ] **Account 5200 missing on existing databases.** `DataSeeder` adds `5200 Cost of goods sold`,
      but `seedAccounts` returns early when accounts exist. Without it, cost of sales falls back to
      `5000 Operating expenses`. Add it manually via Chart of Accounts.

- [ ] **Balance sheet does not balance.** Seeded opening balances are out by **10,700,000** on the
      debit side. Fix at `/accounting/opening-balances` — the page shows the difference and turns
      green at zero.
- [ ] **Input VAT account missing on existing databases — confirmed live.** `DataSeeder` adds
      `1402 VAT receivable (input)`, but `seedAccounts` returns early when accounts exist. Without
      it, `BillService` and `ExpenseService` both fall back to debiting `2101`, which nets VAT
      instead of separating input and output. Verified on the dev database: posting a test expense
      debited `2101 VAT payable` for the input VAT. Add `1402` manually via Chart of Accounts, then
      re-check. Nothing else needs changing — both services prefer `1402` when it exists.
- [ ] **No `ESTIMATE` numbering sequence on existing databases.** `DataSeeder.seedNumbering` returns
      early when any sequence exists, so databases predating it have `INVOICE`, `BILL`,
      `CREDIT_NOTE`, `SALE_ORDER`, `PURCHASE_ORDER`, `EXPENSE` and `JOURNAL` but no `ESTIMATE`.
      Estimates then fall back to a random number like `EST-2026-11501` instead of `EST-0001`.
      Add the row at `/settings/numbering` — same class of drift as the missing 1402 and 5200
      accounts.
- [ ] **Six message keys missing in all three bundles** (pre-existing, render as `??key??`):
      `page.title`, `set.showing`, `set.rates.addHint`, `set.role.count`, `set.workflows.steps`,
      `set.fiscalYear.periodLabel`
- [ ] **Cash flow uses a simplified classification**, not the IFRS indirect method. Each entry is
      bucketed by its largest non-cash account.
- [ ] **Zero-rated vs exempt on old lines.** Lines saved before `TaxRate` existed default to
      zero-rated when the rate is 0%; re-pick the rate to mark them exempt.
- [ ] **Tax filing payments do not post to the GL.** Recording a payment updates the filing only.
      Post the cash settlement as a manual journal entry.

---

## Conventions to keep

- Message keys must exist in **all three** bundles — `messages.properties`, `_fr`, `_rw`.
  Currently 2,640 each, no drift. (`coa.optional` is defined twice in each file — pre-existing.)
- Accounts `10xx` are cash on hand, `11xx` bank and mobile money — `BankingService` relies on this.
- New modules follow the existing shape: entity → repository → form DTO → service → controller →
  templates. `InvoiceService` is the reference for anything that posts to the ledger.
- Documents that post to the GL create a `JournalEntry`; voiding writes a reversing entry rather
  than mutating the original.
