/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : BankFeedService.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : Bringing a statement file in, and deciding what each line is
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.entity.*;
import com.ntaganira.heritier.ibook.enums.FeedImportStatus;
import com.ntaganira.heritier.ibook.enums.FeedLineStatus;
import com.ntaganira.heritier.ibook.enums.JournalEntryStatus;
import com.ntaganira.heritier.ibook.enums.JournalEntryType;
import com.ntaganira.heritier.ibook.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Brings a bank statement file in, and lets somebody say what each line is.
 *
 * <p><strong>This is a file import, not a bank connection.</strong> Nothing dials out: a live feed
 * needs credentials and an agreement with the bank, and neither exists here. What does exist is the
 * CSV every bank will export, and reading that is the part that makes reconciliation and
 * categorisation possible. The page says so rather than implying a connection that is not there.
 *
 * <p><strong>Importing posts nothing.</strong> Lines arrive uncategorised and stay that way until
 * somebody says which account each belongs to, because a 40,000 debit could be rent, a loan
 * repayment, a drawing or a theft and only a person knows which. Writing entries on arrival would
 * be posting from somebody else's document with nobody in the loop.
 *
 * <p>Posting a line writes <em>one</em> balanced entry — the bank account against the account
 * chosen — and stamps the entry number on the line, so the same movement cannot be posted twice.
 * Ignoring a line records that somebody looked and decided it needed nothing, which is different
 * from a line nobody ever dealt with.
 *
 * <p>Nothing is guessed. There is no rule engine and no learning from past decisions: a wrong guess
 * that posts silently is worse than a line that waits. The worklist exists so the waiting is
 * visible.
 */
@Service
public class BankFeedService {

    private static final String MODULE = "banking";
    private static final int MAX_LINES = 5000;

    /** The date formats a bank export is likely to use, tried in order. */
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd/MM/yy"));

    private final BankFeedImportRepository bankFeedImportRepository;
    private final BankFeedLineRepository bankFeedLineRepository;
    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final NumberingSequenceRepository numberingSequenceRepository;
    private final AuditService auditService;

    public BankFeedService(BankFeedImportRepository bankFeedImportRepository,
                           BankFeedLineRepository bankFeedLineRepository,
                           AccountRepository accountRepository,
                           JournalEntryRepository journalEntryRepository,
                           NumberingSequenceRepository numberingSequenceRepository,
                           AuditService auditService) {
        this.bankFeedImportRepository = bankFeedImportRepository;
        this.bankFeedLineRepository = bankFeedLineRepository;
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.numberingSequenceRepository = numberingSequenceRepository;
        this.auditService = auditService;
    }

    // ----- Queries -----

    @Transactional(readOnly = true)
    public Page<BankFeedImport> imports(String q, Long accountId, Pageable pageable) {
        return bankFeedImportRepository.search(trimToNull(q), accountId, pageable);
    }

    @Transactional(readOnly = true)
    public BankFeedImport getImport(Long id) {
        return id == null ? null : bankFeedImportRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<BankFeedLine> linesOf(Long importId) {
        return importId == null ? List.of()
                : bankFeedLineRepository.findByFeedImportIdOrderByLineDateAscIdAsc(importId);
    }

    @Transactional(readOnly = true)
    public Page<BankFeedLine> lines(String q, Long accountId, String status, Pageable pageable) {
        return bankFeedLineRepository.search(trimToNull(q), accountId, parseLineStatus(status), pageable);
    }

    @Transactional(readOnly = true)
    public BankFeedLine getLine(Long id) {
        return id == null ? null : bankFeedLineRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Account> bankAccounts() {
        List<Account> accounts = new ArrayList<>();
        for (Account account : accountRepository.findByActiveTrueOrderByCodeAsc()) {
            if (BankingService.isBankingAccount(account)) {
                accounts.add(account);
            }
        }
        return accounts;
    }

    /** Every account a line could be categorised to, which is everything except the bank itself. */
    @Transactional(readOnly = true)
    public List<Account> categoryAccounts() {
        return accountRepository.findByActiveTrueOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public FeedSummary summary() {
        return new FeedSummary(
                bankFeedImportRepository.count(),
                bankFeedLineRepository.countWaiting(),
                bankFeedLineRepository.countByStatus(FeedLineStatus.POSTED),
                bankFeedLineRepository.countByStatus(FeedLineStatus.IGNORED),
                zero(bankFeedLineRepository.waitingNet()));
    }

    // ----- Importing -----

    /**
     * Reads a CSV statement.
     *
     * <p>Column order is taken from the header rather than assumed, because no two banks export the
     * same shape. A row that cannot be read is <strong>counted and reported</strong> rather than
     * skipped quietly: a statement that silently lost three rows would reconcile to the wrong
     * figure and nothing would say why.
     */
    @Transactional
    public ImportResult importFile(Long accountId, MultipartFile file, String username) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a statement file to bring in");
        }
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Choose the account this statement is for"));
        if (!BankingService.isBankingAccount(account)) {
            throw new IllegalArgumentException(account.getCode() + " is not a cash or bank account. "
                    + "Only 10xx and 11xx accounts hold money a bank can send a statement about.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("That file could not be read");
        }
        String checksum = sha256(bytes);
        List<BankFeedImport> already = bankFeedImportRepository.sameFile(accountId, checksum);

        List<ParsedRow> rows = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        parse(bytes, rows, rejected);

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("No usable rows were found. The file needs a header "
                    + "row naming a date column, a description column, and either a single amount "
                    + "column or separate money-in and money-out columns."
                    + (rejected.isEmpty() ? "" : " " + rejected.size() + " rows could not be read."));
        }

        BankFeedImport feed = BankFeedImport.builder()
                .reference(nextReference())
                .accountId(account.getId())
                .accountCode(account.getCode())
                .accountName(account.getName())
                .fileName(file.getOriginalFilename())
                .checksum(checksum)
                .importedAt(LocalDateTime.now())
                .importedBy(username)
                .status(FeedImportStatus.IMPORTED)
                .build();

        BigDecimal totalIn = BigDecimal.ZERO;
        BigDecimal totalOut = BigDecimal.ZERO;
        LocalDate first = null;
        LocalDate last = null;
        int order = 0;
        for (ParsedRow row : rows) {
            feed.addLine(BankFeedLine.builder()
                    .accountId(account.getId())
                    .accountCode(account.getCode())
                    .accountName(account.getName())
                    .lineDate(row.date())
                    .description(row.description())
                    .reference(row.reference())
                    .moneyIn(row.moneyIn())
                    .moneyOut(row.moneyOut())
                    .runningBalance(row.balance())
                    .status(FeedLineStatus.UNCATEGORISED)
                    .sortOrder(order++)
                    .build());
            totalIn = totalIn.add(row.moneyIn());
            totalOut = totalOut.add(row.moneyOut());
            if (first == null || row.date().isBefore(first)) {
                first = row.date();
            }
            if (last == null || row.date().isAfter(last)) {
                last = row.date();
            }
        }
        feed.setLineCount(rows.size());
        feed.setSkippedCount(rejected.size());
        feed.setTotalIn(totalIn);
        feed.setTotalOut(totalOut);
        feed.setFirstLineDate(first);
        feed.setLastLineDate(last);

        BankFeedImport saved = bankFeedImportRepository.save(feed);
        auditService.log(MODULE, "IMPORT_STATEMENT", "bankFeedImport#" + saved.getId(),
                saved.getReference() + " — " + rows.size() + " lines on " + account.getCode());
        return new ImportResult(saved, rejected, already);
    }

    private void parse(byte[] bytes, List<ParsedRow> rows, List<String> rejected) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new java.io.ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return;
            }
            // A byte-order mark on the first cell would otherwise make the date column unfindable.
            if (!headerLine.isEmpty() && headerLine.charAt(0) == '﻿') {
                headerLine = headerLine.substring(1);
            }
            List<String> header = splitCsv(headerLine);
            Columns columns = Columns.from(header);
            if (!columns.usable()) {
                return;
            }

            String line;
            int lineNo = 1;
            while ((line = reader.readLine()) != null && rows.size() < MAX_LINES) {
                lineNo++;
                if (line.isBlank()) {
                    continue;
                }
                List<String> cells = splitCsv(line);
                try {
                    ParsedRow row = columns.read(cells);
                    if (row == null) {
                        rejected.add("Row " + lineNo);
                    } else {
                        rows.add(row);
                    }
                } catch (RuntimeException ex) {
                    rejected.add("Row " + lineNo);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("That file could not be read");
        }
    }

    /** Minimal CSV splitting: commas separate, double quotes group, doubled quotes escape. */
    private static List<String> splitCsv(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',' || c == ';' || c == '\t') {
                cells.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString().trim());
        return cells;
    }

    // ----- Deciding what a line is -----

    /**
     * Posts a line to the account somebody chose. One balanced entry: the bank account against
     * that account, in the direction the statement says the money went.
     */
    @Transactional
    public BankFeedLine categorise(Long lineId, Long categoryAccountId, String note, String username) {
        BankFeedLine line = bankFeedLineRepository.findById(lineId).orElseThrow();
        if (line.isPosted()) {
            throw new IllegalStateException("This line is already in the books as "
                    + line.getJournalEntryNo() + ". Posting it again would enter the same movement "
                    + "twice.");
        }
        Account bank = accountRepository.findById(line.getAccountId())
                .orElseThrow(() -> new IllegalStateException("The bank account no longer exists"));
        Account category = accountRepository.findById(categoryAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Choose what this line is"));
        if (category.getId().equals(bank.getId())) {
            throw new IllegalArgumentException("A line cannot be categorised to the bank account it "
                    + "is already on — that would post nothing at all");
        }
        BigDecimal amount = line.getMagnitude();
        if (amount.signum() == 0) {
            throw new IllegalArgumentException("A line for nothing cannot be posted");
        }

        boolean moneyIn = line.isMoneyIn();
        JournalEntry entry = JournalEntry.builder()
                .entryNo(nextJournalNo())
                .entryDate(line.getLineDate())
                .type(moneyIn ? JournalEntryType.RECEIPT : JournalEntryType.PAYMENT)
                .status(JournalEntryStatus.POSTED)
                .reference(line.getReference() == null
                        ? line.getFeedImport().getReference() : line.getReference())
                .memo(line.getDescription() == null
                        ? "Bank statement line" : line.getDescription())
                .createdBy(username)
                .build();
        // Money arriving debits the bank and credits what it was for; money leaving does the
        // reverse. The statement's own direction decides, never a guess.
        entry.addLine(JournalLine.builder()
                .accountId(bank.getId())
                .accountCode(bank.getCode())
                .accountName(bank.getName())
                .memo(line.getDescription())
                .debit(moneyIn ? amount : BigDecimal.ZERO)
                .credit(moneyIn ? BigDecimal.ZERO : amount)
                .sortOrder(0)
                .build());
        entry.addLine(JournalLine.builder()
                .accountId(category.getId())
                .accountCode(category.getCode())
                .accountName(category.getName())
                .memo(line.getDescription())
                .debit(moneyIn ? BigDecimal.ZERO : amount)
                .credit(moneyIn ? amount : BigDecimal.ZERO)
                .sortOrder(1)
                .build());
        entry.setTotalDebits(amount);
        entry.setTotalCredits(amount);
        JournalEntry savedEntry = journalEntryRepository.save(entry);

        line.setCategoryAccountId(category.getId());
        line.setCategoryAccountCode(category.getCode());
        line.setCategoryAccountName(category.getName());
        line.setJournalEntryId(savedEntry.getId());
        line.setJournalEntryNo(savedEntry.getEntryNo());
        line.setStatus(FeedLineStatus.POSTED);
        line.setDecidedBy(username);
        line.setDecidedAt(LocalDateTime.now());
        line.setNote(trimToNull(note));
        BankFeedLine saved = bankFeedLineRepository.save(line);

        auditService.log(MODULE, "CATEGORISE_FEED_LINE", "bankFeedLine#" + lineId,
                amount.toPlainString() + " to " + category.getCode() + " as " + savedEntry.getEntryNo());
        return saved;
    }

    /**
     * Records that somebody looked at a line and it needs nothing — usually because the movement is
     * already in the books from the document side. Writes nothing to the ledger.
     */
    @Transactional
    public BankFeedLine ignore(Long lineId, String note, String username) {
        BankFeedLine line = bankFeedLineRepository.findById(lineId).orElseThrow();
        if (line.isPosted()) {
            throw new IllegalStateException("This line is already in the books as "
                    + line.getJournalEntryNo() + " and cannot be set aside");
        }
        line.setStatus(FeedLineStatus.IGNORED);
        line.setDecidedBy(username);
        line.setDecidedAt(LocalDateTime.now());
        line.setNote(trimToNull(note));
        BankFeedLine saved = bankFeedLineRepository.save(line);
        auditService.log(MODULE, "IGNORE_FEED_LINE", "bankFeedLine#" + lineId,
                saved.getMagnitude().toPlainString()
                        + (note == null || note.isBlank() ? "" : " — " + note.trim()));
        return saved;
    }

    /** Puts an ignored line back in the worklist. A posted one cannot come back. */
    @Transactional
    public BankFeedLine reopen(Long lineId) {
        BankFeedLine line = bankFeedLineRepository.findById(lineId).orElseThrow();
        if (line.isPosted()) {
            throw new IllegalStateException("This line is in the books as " + line.getJournalEntryNo()
                    + ". Void that entry from the journal if it was wrong — a line cannot be "
                    + "un-posted from here.");
        }
        line.setStatus(FeedLineStatus.UNCATEGORISED);
        line.setDecidedBy(null);
        line.setDecidedAt(null);
        BankFeedLine saved = bankFeedLineRepository.save(line);
        auditService.log(MODULE, "REOPEN_FEED_LINE", "bankFeedLine#" + lineId,
                saved.getMagnitude().toPlainString());
        return saved;
    }

    /**
     * Discards an import. Lines already posted keep their entries — an entry in the ledger is a
     * fact — and the import is marked rather than deleted so it is clear what happened.
     */
    @Transactional
    public BankFeedImport discard(Long importId, String reason, String username) {
        BankFeedImport feed = bankFeedImportRepository.findById(importId).orElseThrow();
        long posted = bankFeedLineRepository.findByFeedImportIdOrderByLineDateAscIdAsc(importId)
                .stream().filter(BankFeedLine::isPosted).count();
        feed.setStatus(FeedImportStatus.DISCARDED);
        feed.setNotes(reason == null || reason.isBlank() ? feed.getNotes() : reason.trim());
        BankFeedImport saved = bankFeedImportRepository.save(feed);
        auditService.log(MODULE, "DISCARD_STATEMENT", "bankFeedImport#" + importId,
                saved.getReference() + " discarded — " + posted
                        + " already-posted lines keep their entries");
        return saved;
    }

    @Transactional
    public void delete(Long importId) {
        BankFeedImport feed = bankFeedImportRepository.findById(importId).orElse(null);
        if (feed == null) {
            return;
        }
        long posted = bankFeedLineRepository.findByFeedImportIdOrderByLineDateAscIdAsc(importId)
                .stream().filter(BankFeedLine::isPosted).count();
        if (posted > 0) {
            throw new IllegalStateException(posted + (posted == 1 ? " line" : " lines")
                    + " from this statement are in the books. Discard it instead — deleting it "
                    + "would leave those entries with nothing explaining where they came from.");
        }
        bankFeedImportRepository.delete(feed);
        auditService.log(MODULE, "DELETE_STATEMENT", "bankFeedImport#" + importId,
                feed.getReference() + " deleted before anything was posted");
    }

    // ----- Helpers -----

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            return null;
        }
    }

    private String nextReference() {
        return "STM-" + LocalDate.now().getYear() + "-"
                + String.format("%05d", (int) (bankFeedImportRepository.count() + 1));
    }

    private String nextJournalNo() {
        NumberingSequence seq = numberingSequenceRepository.findByDocType("JOURNAL").orElse(null);
        if (seq != null) {
            long next = seq.getNextNumber();
            String prefix = seq.getPrefix() == null ? "JE-" : seq.getPrefix();
            int padding = seq.getPadding() == 0 ? 5 : seq.getPadding();
            String number = prefix + String.format("%0" + padding + "d", next);
            seq.setNextNumber(next + 1);
            numberingSequenceRepository.save(seq);
            return number;
        }
        return "JE-" + LocalDate.now().getYear() + "-00000";
    }

    static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(trimmed, format);
            } catch (RuntimeException ignored) {
                // Try the next shape; a value no format reads is reported, never guessed at.
            }
        }
        return null;
    }

    /**
     * Reads a money cell. Thousands separators, currency words and brackets for negatives all turn
     * up in bank exports, and a comma may be either a thousands separator or a decimal point
     * depending on where the file came from.
     */
    static BigDecimal parseMoney(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        String cleaned = value.trim().replaceAll("[^0-9,.\\-()]", "");
        if (cleaned.isEmpty()) {
            return BigDecimal.ZERO;
        }
        boolean negative = cleaned.startsWith("(") && cleaned.endsWith(")");
        cleaned = cleaned.replace("(", "").replace(")", "");
        int lastComma = cleaned.lastIndexOf(',');
        int lastDot = cleaned.lastIndexOf('.');
        if (lastComma > lastDot) {
            // A comma after the last dot means the comma is the decimal point: 1.234,56
            cleaned = cleaned.replace(".", "").replace(',', '.');
        } else {
            cleaned = cleaned.replace(",", "");
        }
        try {
            BigDecimal amount = new BigDecimal(cleaned);
            return negative ? amount.negate() : amount;
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private static FeedLineStatus parseLineStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return FeedLineStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Where each thing is in the file, worked out from the header. Order is never assumed because no
     * two banks export the same shape.
     */
    record Columns(int date, int description, int reference, int amount,
                   int moneyIn, int moneyOut, int balance) {

        static Columns from(List<String> header) {
            int date = -1;
            int description = -1;
            int reference = -1;
            int amount = -1;
            int in = -1;
            int out = -1;
            int balance = -1;
            for (int i = 0; i < header.size(); i++) {
                String name = header.get(i).toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
                if (date < 0 && name.contains("date")) {
                    date = i;
                } else if (description < 0 && (name.contains("description") || name.contains("narrative")
                        || name.contains("details") || name.contains("particulars")
                        || name.contains("memo"))) {
                    description = i;
                } else if (reference < 0 && (name.contains("reference") || name.equals("ref")
                        || name.contains("chequeno") || name.contains("transactionid"))) {
                    reference = i;
                } else if (in < 0 && (name.contains("credit") || name.contains("moneyin")
                        || name.contains("deposit") || name.contains("paidin"))) {
                    in = i;
                } else if (out < 0 && (name.contains("debit") || name.contains("moneyout")
                        || name.contains("withdrawal") || name.contains("paidout"))) {
                    out = i;
                } else if (balance < 0 && name.contains("balance")) {
                    balance = i;
                } else if (amount < 0 && name.contains("amount")) {
                    amount = i;
                }
            }
            return new Columns(date, description, reference, amount, in, out, balance);
        }

        /** A date plus either one amount column or a pair is the least that can be read. */
        boolean usable() {
            return date >= 0 && (amount >= 0 || moneyIn >= 0 || moneyOut >= 0);
        }

        ParsedRow read(List<String> cells) {
            LocalDate when = parseDate(cell(cells, date));
            if (when == null) {
                return null;
            }
            BigDecimal in = BigDecimal.ZERO;
            BigDecimal out = BigDecimal.ZERO;
            if (moneyIn >= 0 || moneyOut >= 0) {
                in = parseMoney(cell(cells, moneyIn)).abs();
                out = parseMoney(cell(cells, moneyOut)).abs();
            }
            if (in.signum() == 0 && out.signum() == 0 && amount >= 0) {
                // A single signed amount column: the sign says which way the money went.
                BigDecimal signed = parseMoney(cell(cells, amount));
                if (signed.signum() >= 0) {
                    in = signed;
                } else {
                    out = signed.negate();
                }
            }
            if (in.signum() == 0 && out.signum() == 0) {
                return null;
            }
            return new ParsedRow(when, cell(cells, description), cell(cells, reference),
                    in, out, balance >= 0 ? parseMoney(cell(cells, balance)) : null);
        }

        private static String cell(List<String> cells, int index) {
            if (index < 0 || index >= cells.size()) {
                return null;
            }
            String value = cells.get(index);
            return value == null || value.isBlank() ? null : value.trim();
        }
    }

    record ParsedRow(LocalDate date, String description, String reference,
                     BigDecimal moneyIn, BigDecimal moneyOut, BigDecimal balance) {}

    public record ImportResult(BankFeedImport feed, List<String> rejected,
                               List<BankFeedImport> duplicatesOf) {

        public boolean hasRejected() {
            return !rejected.isEmpty();
        }

        public boolean isDuplicate() {
            return !duplicatesOf.isEmpty();
        }
    }

    public record FeedSummary(long imports, long waiting, long posted, long ignored,
                              BigDecimal waitingNet) {}
}
