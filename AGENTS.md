# AGENTS.md — iBook / Ebook Online

Project-specific notes for AI agents working in this repository.

## Owner / Contact
- Name: Heritier
- Phone: +250 788 533 669
- Email: ntaganira71@gmail.com

## Project
- Cloud accounting platform for Rwanda (RWF).
- Spring Boot 3.4.1, Java 17, Thymeleaf (fragment layout), PostgreSQL, MinIO (via docker-compose).
- Single `PageController` serves all Thymeleaf views; UI scaffold currently in progress.

## Build / Run (Windows PowerShell)
- JAVA_HOME: `C:\Program Files (x86)\Android\openjdk\jdk-17.0.14` — the PATH default is JDK 8; set `$env:JAVA_HOME` before every Maven call.
- Compile: `.\mvnw.cmd -q compile`
- Run (background): `.\mvnw.cmd spring-boot:run` — log at `C:\Users\Muganga\AppData\Local\Temp\opencode\ibook-run.log`
- **Important:** `spring-boot:run` serves from `target/classes`, not `src`. After editing any `src/main/resources` file, sync with: `.\mvnw.cmd -q resources:resources`. DevTools autorestarts.
- Stack: `docker compose up -d` starts `ibook-postgres` (port 5436) and `ibook-minio` (ports 9008/9009).
- App URL: `http://localhost:8080`

## i18n
- Locales: English (default), French, Kinyarwanda. Cookie `EBOOK_LOCALE`; switch via `?lang=en|fr|rw`.
- Topbar language switcher must show flag icons (EN/FR/RW) with the active language highlighted.
- Infra: `WebConfig.java` (`CookieLocaleResolver` + `LocaleChangeInterceptor`).
- Bundles in `src/main/resources`: `messages.properties`, `messages_fr.properties`, `messages_rw.properties`. Key prefixes: `app.*`, `common.*`, `auth.*`, `nav.*`, `dash.*`, `coa.*`, `cust.*`, `inv.*`, `je.*`, `tran.*`, `rpt.*`, `error.*`, etc.
- All 3 bundles have identical key sets (789 keys each as of last check). Keep parity when adding keys — duplicate in all 3 bundles.

## Conventions
- No code comments unless requested.
- Sample/business data (names, amounts, dates, descriptions) stays hardcoded; only UI chrome, labels, statuses, badges use `#{}` message keys.