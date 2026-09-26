# CLAUDE.md — iBook / Ebook Online

Cloud accounting platform for Rwanda. Spring Boot 3.4.1, Java 17, Thymeleaf, PostgreSQL.

`AGENTS.md` has build/run and contact details but is **stale** on project state — trust this file
and `TODO.md` instead.

## Before you finish any task

```powershell
$env:JAVA_HOME = "C:\Program Files (x86)\Android\openjdk\jdk-17.0.14"   # PATH default is JDK 8
.\mvnw.cmd -q compile
```

Nothing is done until this passes. `spring-boot:run` serves from `target/classes`, so after editing
anything under `src/main/resources` run `.\mvnw.cmd -q resources:resources` or the change won't show.


## Schema belongs to Flyway, not to Hibernate

`ddl-auto` is `validate`. Hibernate checks the schema and refuses to start if it disagrees with
the entities; it no longer creates or alters anything. **A new entity, column or enum value needs a
migration in `src/main/resources/db/migration`, or the app will not start.**

- `V1__baseline.sql` is the schema as of 2026-09-26 and **never runs on an existing database** —
  Flyway baselines those at version 1 and treats it as applied. Put nothing new in it.
- New work starts at `V3__`.
- Adding an enum value means adding it to the Java enum **and** shipping a migration that widens
  the matching `<table>_<column>_check` constraint. There are 74 such constraints.
- Adopting this on a fresh environment whose database already has an **empty**
  `flyway_schema_history` table needs that table dropped once, or Flyway tries to apply V1 and
  fails with `relation "accounting_periods" already exists`.

Back up before schema work: `docker exec ibook-postgres pg_dump -U postgres -d ibook -Fc > backups/x.dump`
(`backups/` is gitignored). Note Git Bash rewrites in-container `/tmp` paths, so redirect stdout
rather than passing `--file`.

## Non-negotiable conventions

**1. Message bundles must stay in parity.** Every `#{key}` needs an entry in all three of
`messages.properties`, `messages_fr.properties`, `messages_rw.properties` (English, French,
Kinyarwanda). Never add to one without the others. Verify:

```bash
for f in messages messages_fr messages_rw; do grep -c '=' src/main/resources/$f.properties; done
```

Those six missing keys are resolved. Five were added to all three bundles;
`page.title` was not, because it appears only inside a usage comment in
`layout/base.html` and is never rendered. Bundles are at 5,147 keys each.

**2. No code comments unless they explain *why*.** No comment restating what the line does.

**3. Module shape.** Follow it exactly:
`entity → repository → form DTO → service → controller → templates`.
`InvoiceService` is the reference for anything that posts to the ledger.

**4. Every posting document writes a balanced `JournalEntry`.** Voiding creates a *reversing* entry;
it never mutates or deletes the original. Rounding drift goes onto the last line so debits equal
credits exactly.

**5. Controllers are thin.** Business logic and all `@Transactional` work live in services.
Records returned from services are the view model; controllers only map them into `Model`.

**6. Forms are records** (`CustomerForm`, `TaxRateForm`) *except* where lines need
`AutoPopulatingList` (`InvoiceForm`, `BillForm`, `EstimateForm`) — those are classes with getters
and setters. Thymeleaf binds records via `name="x"` + `th:value="${form.x}"`, not `th:field`.

## Chart-of-accounts codes the code depends on

| Code | Used by |
|---|---|
| 1201 | Accounts receivable — invoice posting |
| 1301 | Inventory — bill capitalisation, COGS, stock adjustments |
| 1402 | VAT receivable (input) — bill posting |
| 2001 | Accounts payable — bill posting |
| 2101 | VAT payable (output) — invoice posting |
| 5200 | Cost of goods sold — invoice COGS, stock adjustments |

`10xx` = cash on hand, `11xx` = bank and mobile money. `BankingService` relies on this.

**Existing databases are missing 1402 and 5200.** `DataSeeder.seedAccounts` returns early when any
account exists, so these only appear on a fresh install. Both have fallbacks, which silently produce
wrong numbers. Don't "fix" the fallbacks — the accounts need adding via Chart of Accounts.

## Traps that have already bitten

- **Never generate a template by find-and-replace from another module without verifying after.**
  It repeatedly leaves stale refs like `invoice.total` *nested inside* larger expressions
  (`#numbers.formatDecimal(invoice.total, ...)`), which a `${invoice.` search misses. After any such
  copy, check every `${var.prop}` against the actual entity fields and record components.
- **Sidebar links ≠ working pages.** The sidebar lists 115 routes; ~57 are implemented. Check for a
  `@GetMapping` before assuming a page exists.
- **Don't trust a truncated file listing.** This project has 500+ files. `head` on a `find` will
  silently hide entire modules.
- Multi-currency totals are summed without conversion across payments, statements and aging. Correct
  for single-currency parties, wrong otherwise. Don't paper over it.

## Working agreement

- Read `TODO.md` first. It tracks all 115 routes by tier and lists known issues.
- Tick the item off in `TODO.md` and update the mapped count when you finish one.
- One module per change. Don't start the next one unprompted.
- If a design decision has real accounting consequences (posting rules, costing method, tax
  treatment), state the choice and its trade-off rather than picking silently.
- Say plainly when something is unverified. Don't claim a build passes without running it.
