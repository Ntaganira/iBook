# iBook — Build Tracker

Progress against the sidebar, which lists **115 routes**. A route counts as done when it has
a controller mapping, a template, and reads real data.

**75 / 115 mapped · 40 remaining**

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
- [x] `/inventory/brands` — CRUD with edit in place, activate/deactivate, delete blocked while in
      use. `Product.brand` becomes a denormalised name alongside a new `brandId`, mirroring
      `categoryId`/`categoryName`; the product form now picks from a list instead of free text. An
      explicit **import** action folds brand names already typed on products into real brands and
      links them — nothing is migrated silently.
- [x] `/inventory/bundles` — a bundle is a stocked product plus a recipe of components.
      **Assembled, not virtual**: assembling consumes the parts and creates bundle stock at exactly
      the rolled-up component cost, so inventory value is conserved and nothing reaches the ledger;
      taking one apart is the reverse. Saving a bundle sets the bundle product's cost price to the
      roll-up so cost of sales matches what an assembly capitalised.
- [x] `/inventory/transfers` — draft/complete/cancel/void, paired `TRANSFER_OUT`/`TRANSFER_IN`
      movements at the two locations, per-location stock check before the stock moves. The move
      itself is non-posting; only stock that fails to arrive is written off from Inventory (1301)
      to Cost of sales (5200). Voiding writes mirror movements plus a reversing entry.
- [x] `/inventory/counts` — count sheet per location pre-filled with what the books say, draft /
      post / cancel / void. Posting measures the variance against stock on hand **at that moment**,
      not against the sheet's opening snapshot, so a sale during the count is not silently undone;
      surpluses debit Inventory (1301) and credit Cost of sales (5200), shortages do the reverse,
      posted gross in one entry. Voiding writes counter movements plus a reversing entry.
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

### Fixed assets (6) — COMPLETE
`FixedAsset`, `DepreciationEntry`. Depreciation posts to the GL — mirrors the invoice posting pattern.

- [x] `/assets/register` — draft/in-service, straight-line and reducing-balance schedules, residual
      value, opening accumulated depreciation for assets entered part-way through their life, and
      per-asset account choices (defaults 1501 / 1509 / 5006). **Non-posting**: the bill or expense
      that bought the asset already put it in the books, so registering it again would double-count.
      The schedule and the "due but not yet posted" figure are worked out for `/assets/depreciation`
      to charge.
- [x] `/assets/categories` · `/assets/locations` — both promoted out of free text the way brands
      were: `FixedAsset` gains `categoryId` and `locationId` beside the denormalised names, the
      register form picks from lists instead of typing, and an explicit **import** folds the names
      already typed on assets into real records. A category is more than a label — it carries a
      **depreciation policy** (method, useful life, reducing-balance rate) and the three accounts,
      which a new asset picks up through a visible *"fill this form from the category's policy"*
      action rather than silently on save; the asset keeps whatever is saved on it, so changing the
      category later never rewrites an asset. Locations carry site, address, city and who is
      answerable, and show the net book value standing at each. Free text that has not been imported
      is **preserved** rather than wiped when an asset is edited, since the picker cannot offer it.

- [x] `/assets/depreciation` — prepare a run to a date, preview what each asset owes, post or void.
      Charges **Dr depreciation expense / Cr accumulated depreciation** in one entry per run, grouped
      by account pair rather than one entry per asset, with rounding drift on the last line. Each
      asset's share is recomputed **at posting time**, so two drafts over the same period cannot both
      charge it. Voiding writes a reversing entry and takes the charge back off each asset.
- [x] `/assets/disposals` — sold, traded in, scrapped, given away or lost. Posting charges any
      depreciation still owed **at the disposal date** so the net book value is the one the asset
      really has when it leaves, then reverses cost and accumulated depreciation out, brings in the
      proceeds and recognises the balance as a gain or a loss. Voiding puts the asset back into
      service and reverses the lot. `DataSeeder` now adds `4004 Gain on asset disposal` and
      `5007 Loss on asset disposal`.
- [x] `/assets/transfers` — moves an asset to another location, another custodian, or another pair of
      balance sheet accounts. Draft / complete / cancel / void. **A move of place or custody is
      non-posting** — the same asset sits in the same account at the same cost, so nothing reaches
      the ledger. Changing either account *is* a reclassification and posts one balanced entry that
      carries both the cost and the accumulated depreciation across, leaving net book value
      untouched. Where the asset is coming from is read again at completion, so a draft raised
      before another move is not stale. Voiding puts it back and reverses the entry, and is refused
      while a later completed move of the same asset exists. `FixedAsset` gains a `custodian` field
      and `DataSeeder` an `ASSET_TRANSFER` / `ATR-` sequence.

### Projects (7)
`Project`, `TimeEntry`. Job costing tags existing journal lines to a project.

- [ ] `/projects` · `/projects/timesheets` · `/projects/billable` · `/projects/budgets`
- [ ] `/projects/job-costing` · `/projects/profitability` · `/projects/progress-billing`

### Budgets (6)
`Budget`, `BudgetLine`. Budget-vs-actual is a join against GL data that already exists.

- [x] `/budgets` · `/budgets/vs-actual` — a budget is twelve months of planned figures per revenue
      or expense account, draft / approve / reopen / close. **Entirely non-posting**: a plan is not
      a transaction, and approving only locks the figures so a comparison cannot measure against
      something that is still moving. A line is given either month by month or as one yearly figure
      spread evenly, with the rounding drift on the last month so the twelve add back exactly;
      filled-in months always win over the yearly figure. Only revenue and expense accounts can be
      budgeted — a capital budget is a different document and deliberately out of scope.
      `/budgets/vs-actual` reads actuals through `ReportService.signedMovementsBetween`, the same
      definition the profit and loss uses, so the two cannot drift apart. Variance is reported as
      **favourable or adverse** rather than raw arithmetic, because earning more than planned and
      spending less than planned are both good and have opposite signs. Revenue and expense the
      ledger saw on accounts the budget never named are listed separately rather than folded into
      the variance.
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
- [ ] **Re-saving a bundle silently revalues the bundles already on the shelf.** Saving sets the
      bundle product's cost price to the new roll-up, and `/inventory/valuation` values stock at
      whatever the cost price currently is. Measured: changing one component from 10 to 12 per bundle
      raised reported stock value by **RWF 253,000** across 11 assembled bundles, with no journal
      entry — the assemblies had capitalised them at the old cost. This is the standard-costing gap
      that already applies to editing any product's cost price; bundles just make it automatic. A
      real fix is weighted-average or FIFO costing, which is a much larger change. Until then, treat
      a recipe change as something to do when little or none of the bundle is in stock. The same
      side effect fires on **creating** a bundle: naming an existing product as the thing a bundle is
      sold as overwrites that product's cost price with the roll-up, which is wrong if the product
      was already stocked and priced in its own right. Pick a product that exists only to be the
      bundle.
- [ ] **Nothing reconciles the asset register against the ledger.** The register is deliberately
      non-posting, so an asset can sit in it at a cost that no longer matches account 1501, or be
      missing entirely while 1501 carries its value. `openingAccumulated` is likewise typed in by
      hand and never checked against 1509 — the seeded chart already carries −110,000 on 1509 that
      belongs to no registered asset. A reconciliation screen comparing the register's totals to
      1501/1509 is worth building before the register is trusted for a balance sheet.
- [ ] **`4004` and `5007` are missing on existing databases.** `DataSeeder` now adds
      `4004 Gain on asset disposal` and `5007 Loss on asset disposal`, but `seedAccounts` returns
      early when any account exists, so a database that predates them has neither. `/assets/disposals`
      detects this and says so rather than falling back to something semantically wrong like
      `4003 Interest income` — but until the accounts are added by hand at Chart of Accounts, every
      disposal has to be pointed at a gain and loss account manually. Same class of drift as the
      missing 1402 and 5200.
- [ ] **The depreciation page understates what has actually been charged.** `Charged to date` on
      `/assets/depreciation` sums `DepreciationRun.totalCharge`, so it misses the catch-up
      depreciation a **disposal** charges on its way past — that goes straight to the asset's
      `postedAccumulated` and into the ledger without a run to sum. The asset register and the ledger
      are right; only that one tile is short. Posting a disposal also has no fiscal-period check, the
      same gap as a depreciation run.
- [ ] **A category policy is a snapshot, and asset locations are not warehouses.** Changing a
      category's depreciation policy or accounts never touches assets already in it — the asset
      keeps what was saved on it, which is the right default but means there is no "reapply to
      existing assets" action, so a corrected policy has to be applied asset by asset. Free text
      that was never imported is preserved on edit but cannot be cleared through the form, only
      replaced by picking a real record. Renaming a category or location updates the assets
      carrying it, but a completed **transfer keeps the old name** it recorded, so the transfer
      history and the register can show different names for the same place — deliberate, since that
      is what the place was called when the asset moved. `/assets/locations` and
      `/inventory/warehouses` are separate lists: an asset location is not a stock location, and
      nothing reconciles the two.
- [ ] **An asset transfer is a single step, and nothing reconciles it to a physical count.** There is
      no in-transit state: completing applies the move at once, and the transfer date is free text
      with no fiscal-period check, so a reclassification can be posted into a closed period — the
      same gap as depreciation runs and disposals. A transfer also cannot move an asset's
      **depreciation expense** account, only the cost and accumulated pair, so an asset reclassified
      from office equipment to motor vehicles still charges its old expense account until someone
      edits the register by hand. An accounts-only move shows an identical location pair on the list
      (`Huye branch → Huye branch`) with the account change on the line below it, which reads oddly
      but is accurate.
- [ ] **No `ASSET_TRANSFER` numbering sequence on existing databases.** `DataSeeder.seedNumbering`
      now adds `ATR-`, but it returns early when any sequence exists — observed live:
      `ATR-2026-10092`, `ATR-2026-22296`, `ATR-2026-30678`. Add the row at `/settings/numbering`,
      same class of drift as `TRF-`, `SC-`, `FA-`, `DEP-` and `DIS-`.
- [ ] **A depreciation run has no period lock and no fiscal-period check.** It charges up to any date
      the user picks, including one inside a closed fiscal period, and nothing stops a run dated
      before an earlier run's period end. The protection against double-charging is arithmetic — the
      charge is always "due by that date less what has already been charged" recomputed at posting
      time — not a lock, so a backdated run simply finds nothing owing rather than being refused.
      Straight line and reducing balance both prorate by **whole months** within a year, so an asset
      acquired mid-month earns nothing for the part month.
- [ ] **`FixedAsset.depreciatedTo` is written but never read, and a void does not roll it back.**
      `DepreciationService.post` stamps it with the run's period end; nothing else in the codebase
      reads it — the charge is always derived from `postedAccumulated`, so the field is decorative
      today. After voiding a run the stamp is left at the voided run's date, so an asset can claim to
      be depreciated to December while carrying only September's charge. Harmless until somebody
      trusts it; either roll it back on void or drop the column.
- [ ] **Voiding a depreciation run unwinds every asset, even ones charged again since.** The void
      subtracts each entry's charge from `postedAccumulated` and floors at zero. If a later run has
      already charged the same asset, voiding the earlier run leaves the asset under-depreciated
      relative to its schedule — the next run will notice and catch it up, but the ledger and the
      register disagree until then. Voiding runs newest-first avoids it.
- [ ] **Selling a bundle does not explode it into components.** A bundle has to be assembled before
      it can be sold; invoicing the bundle product without assembling drives its stock negative
      rather than depleting the parts. Making a sale explode the recipe would mean teaching
      `InvoiceService` about bundles, which changes how every invoice posts — deliberately out of
      scope here. Related gaps: a bundle may contain another bundle and assembly does **not**
      cascade, so the inner one must be assembled first; the component costs are a snapshot taken
      when the bundle was last saved, so a component price rise only reaches the roll-up on re-save;
      and deleting a bundle removes the recipe while leaving assembled stock and its movements in
      place.
- [ ] **Free-text brands are only folded in when somebody presses the button.** `Product.brand` is
      still the denormalised name and still accepts whatever was there before, so a database that
      predates brands keeps its typed values until the import on `/inventory/brands` is run. Two
      spellings of one name collapse case-insensitively into the first one seen, which is usually
      right but is a guess — check the list before importing. Products imported this way get no code,
      manufacturer or website. Deactivating a brand hides it from the product form but leaves it on
      the products already carrying it, which is deliberate.
- [ ] **A stock count is not a freeze, and it counts one location at a time.** Nothing stops stock
      moving while a sheet is open. Posting handles that by measuring the variance against on-hand at
      the moment of posting rather than the opening snapshot — the line is flagged as drifted when
      the two differ — but the counted figure itself is still whatever was on the shelf when somebody
      looked, so a sale between counting and posting is written off as a shortage. The honest fix is
      to stop trading during a count. A sheet also covers a single warehouse, so a company-wide count
      means one sheet per location, and stock that arrived without a location sits at the default
      warehouse (see below), which is where its variance will be posted.
- [ ] **No `STOCK_COUNT` or `TRANSFER` numbering sequence on existing databases.** `DataSeeder.seedNumbering` now
      adds `TRF-`, `SC-` and `FA-`, but it returns early when any sequence exists, so databases
      predating them get random fallbacks — observed live: `TRF-2026-53659`, `SC-2026-29322` and
      `FA-2026-00948`. Add all three rows at `/settings/numbering` — same class of drift as the
      missing `ESTIMATE` sequence and the missing 1402 and 5200 accounts.
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
  Currently 3,330 each, no drift. (`coa.optional` is defined twice in each file — pre-existing.)
- Accounts `10xx` are cash on hand, `11xx` bank and mobile money — `BankingService` relies on this.
- New modules follow the existing shape: entity → repository → form DTO → service → controller →
  templates. `InvoiceService` is the reference for anything that posts to the ledger.
- Documents that post to the GL create a `JournalEntry`; voiding writes a reversing entry rather
  than mutating the original.
