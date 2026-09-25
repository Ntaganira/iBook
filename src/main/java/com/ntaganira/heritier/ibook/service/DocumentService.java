/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : DocumentService.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : The document library: what is stored, what it belongs to, and who may take it back
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.DocumentEditForm;
import com.ntaganira.heritier.ibook.dto.DocumentUploadForm;
import com.ntaganira.heritier.ibook.entity.Attachment;
import com.ntaganira.heritier.ibook.enums.DocumentCategory;
import com.ntaganira.heritier.ibook.enums.DocumentLinkType;
import com.ntaganira.heritier.ibook.repository.*;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The document library.
 *
 * <p>Nothing here posts. A stored file is evidence for a transaction, never a transaction, so
 * attaching a receipt to an expense changes no figure anywhere — it only makes the paper findable
 * from the record it supports.
 *
 * <p>Two rules follow from that and are enforced rather than advised. A file attached to a record
 * cannot be deleted while the link stands, because deleting it destroys the evidence for something
 * the books still claim; it has to be detached first, which is a decision somebody makes on
 * purpose. And archiving is the ordinary way to put a document away: it keeps both the row and the
 * bytes, where deleting removes the file from the store for good.
 */
@Service
public class DocumentService {

    private static final String MODULE = "documents";

    private final AttachmentRepository attachmentRepository;
    private final DocumentStorage storage;
    private final CustomerRepository customerRepository;
    private final VendorRepository vendorRepository;
    private final InvoiceRepository invoiceRepository;
    private final BillRepository billRepository;
    private final ExpenseRepository expenseRepository;
    private final ProjectRepository projectRepository;
    private final FixedAssetRepository fixedAssetRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final AuditService auditService;

    public DocumentService(AttachmentRepository attachmentRepository,
                           DocumentStorage storage,
                           CustomerRepository customerRepository,
                           VendorRepository vendorRepository,
                           InvoiceRepository invoiceRepository,
                           BillRepository billRepository,
                           ExpenseRepository expenseRepository,
                           ProjectRepository projectRepository,
                           FixedAssetRepository fixedAssetRepository,
                           JournalEntryRepository journalEntryRepository,
                           AuditService auditService) {
        this.attachmentRepository = attachmentRepository;
        this.storage = storage;
        this.customerRepository = customerRepository;
        this.vendorRepository = vendorRepository;
        this.invoiceRepository = invoiceRepository;
        this.billRepository = billRepository;
        this.expenseRepository = expenseRepository;
        this.projectRepository = projectRepository;
        this.fixedAssetRepository = fixedAssetRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.auditService = auditService;
    }

    // ----- Queries -----

    @Transactional(readOnly = true)
    public Page<Attachment> list(String q, String category, String linkType,
                                 boolean templates, boolean archived, Pageable pageable) {
        return attachmentRepository.search(trimToNull(q), parseCategory(category),
                parseLinkType(linkType), templates, archived, pageable);
    }

    @Transactional(readOnly = true)
    public Attachment get(Long id) {
        return id == null ? null : attachmentRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Attachment> forRecord(DocumentLinkType linkType, Long linkId) {
        if (linkType == null || !linkType.isAttached() || linkId == null) {
            return List.of();
        }
        return attachmentRepository.findByLinkTypeAndLinkIdOrderByCreatedAtDesc(linkType, linkId);
    }

    @Transactional(readOnly = true)
    public LibrarySummary summary() {
        long bytes = attachmentRepository.totalBytes() == null ? 0L : attachmentRepository.totalBytes();
        return new LibrarySummary(
                attachmentRepository.countByArchivedFalseAndTemplateFalse(),
                attachmentRepository.countUnattached(),
                attachmentRepository.countByTemplateTrueAndArchivedFalse(),
                attachmentRepository.countByArchivedTrue(),
                BigDecimal.valueOf(bytes).divide(BigDecimal.valueOf(1024 * 1024), 1,
                        RoundingMode.HALF_UP),
                storage.describeLocation());
    }

    /**
     * Everything attached, grouped by the record it belongs to, plus the files belonging to
     * nothing. The loose pile is shown rather than hidden: an uploaded receipt nobody attached is
     * the normal way paperwork goes missing.
     */
    @Transactional(readOnly = true)
    public AttachmentView attachmentView() {
        Map<String, AttachmentGroup> groups = new LinkedHashMap<>();
        for (Attachment attachment : attachmentRepository.attached()) {
            String key = attachment.getLinkType().name() + "#" + attachment.getLinkId();
            groups.computeIfAbsent(key, k -> new AttachmentGroup(attachment.getLinkType(),
                    attachment.getLinkId(), attachment.getLinkLabel(),
                    attachment.getLinkHref(), new ArrayList<>())).files().add(attachment);
        }
        return new AttachmentView(new ArrayList<>(groups.values()),
                attachmentRepository.unattached());
    }

    /** The records a file may be attached to, for whichever kind was chosen. */
    @Transactional(readOnly = true)
    public List<Choice> choicesFor(DocumentLinkType linkType) {
        if (linkType == null || !linkType.isAttached()) {
            return List.of();
        }
        List<Choice> rows = new ArrayList<>();
        switch (linkType) {
            case CUSTOMER -> customerRepository.findByActiveTrueOrderByNameAsc()
                    .forEach(c -> rows.add(new Choice(c.getId(), c.getName())));
            case VENDOR -> vendorRepository.findByActiveTrueOrderByNameAsc()
                    .forEach(v -> rows.add(new Choice(v.getId(), v.getName())));
            case INVOICE -> invoiceRepository.findAll()
                    .forEach(i -> rows.add(new Choice(i.getId(),
                            i.getInvoiceNo() + " — " + i.getCustomerName())));
            case BILL -> billRepository.findAll()
                    .forEach(b -> rows.add(new Choice(b.getId(),
                            b.getBillNo() + " — " + b.getVendorName())));
            case EXPENSE -> expenseRepository.findAll()
                    .forEach(e -> rows.add(new Choice(e.getId(),
                            e.getExpenseNo() + " — " + e.getPayeeName())));
            case PROJECT -> projectRepository.findAllByOrderByCodeAsc()
                    .forEach(p -> rows.add(new Choice(p.getId(), p.getDisplayName())));
            case FIXED_ASSET -> fixedAssetRepository.findAll()
                    .forEach(a -> rows.add(new Choice(a.getId(),
                            a.getAssetNo() + " — " + a.getName())));
            case JOURNAL_ENTRY -> journalEntryRepository.findAll()
                    .forEach(j -> rows.add(new Choice(j.getId(),
                            j.getEntryNo() + " — " + j.getEntryDate())));
            default -> { }
        }
        rows.sort((a, b) -> a.label().compareToIgnoreCase(b.label()));
        return rows;
    }

    /**
     * The record's name as it reads now. Null means the id points at nothing, which is refused
     * rather than stored — a link to a record that does not exist is worse than no link.
     */
    @Transactional(readOnly = true)
    public String labelFor(DocumentLinkType linkType, Long linkId) {
        if (linkType == null || !linkType.isAttached() || linkId == null) {
            return null;
        }
        return switch (linkType) {
            case CUSTOMER -> customerRepository.findById(linkId).map(c -> c.getName()).orElse(null);
            case VENDOR -> vendorRepository.findById(linkId).map(v -> v.getName()).orElse(null);
            case INVOICE -> invoiceRepository.findById(linkId)
                    .map(i -> i.getInvoiceNo() + " — " + i.getCustomerName()).orElse(null);
            case BILL -> billRepository.findById(linkId)
                    .map(b -> b.getBillNo() + " — " + b.getVendorName()).orElse(null);
            case EXPENSE -> expenseRepository.findById(linkId)
                    .map(e -> e.getExpenseNo() + " — " + e.getPayeeName()).orElse(null);
            case PROJECT -> projectRepository.findById(linkId)
                    .map(com.ntaganira.heritier.ibook.entity.Project::getDisplayName).orElse(null);
            case FIXED_ASSET -> fixedAssetRepository.findById(linkId)
                    .map(a -> a.getAssetNo() + " — " + a.getName()).orElse(null);
            case JOURNAL_ENTRY -> journalEntryRepository.findById(linkId)
                    .map(j -> j.getEntryNo() + " — " + j.getEntryDate()).orElse(null);
            default -> null;
        };
    }

    // ----- Upload -----

    /**
     * Stores the files and records them. Each is handled on its own so one rejected file does not
     * take the rest of the batch down with it, and the count of each outcome is reported.
     */
    @Transactional
    public UploadResult upload(MultipartFile[] files, DocumentUploadForm form, String username) {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("Choose at least one file to upload");
        }
        DocumentLinkType linkType = parseLinkTypeOrNone(form.linkType());
        Long linkId = linkType.isAttached() ? form.linkId() : null;
        String label = null;
        if (linkType.isAttached()) {
            if (linkId == null) {
                throw new IllegalArgumentException(
                        "Choose which record these files belong to, or attach them to nothing");
            }
            label = labelFor(linkType, linkId);
            if (label == null) {
                throw new IllegalArgumentException("That record no longer exists");
            }
        }

        List<Attachment> saved = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            try {
                DocumentStorage.Stored stored = storage.store(file);
                if (!attachmentRepository.findByChecksumAndArchivedFalse(stored.checksum()).isEmpty()) {
                    duplicates.add(safeName(file.getOriginalFilename()));
                }
                Attachment attachment = Attachment.builder()
                        .storageKey(stored.storageKey())
                        .originalName(safeName(file.getOriginalFilename()))
                        .contentType(trimToNull(file.getContentType()))
                        .sizeBytes(stored.sizeBytes())
                        .checksum(stored.checksum())
                        .category(parseCategoryOrOther(form.category()))
                        .linkType(linkType)
                        .linkId(linkId)
                        .linkLabel(label)
                        .description(trimToNull(form.description()))
                        .tags(trimToNull(form.tags()))
                        .template(form.templateValue())
                        .uploadedBy(username)
                        .build();
                saved.add(attachmentRepository.save(attachment));
            } catch (RuntimeException ex) {
                rejected.add(safeName(file.getOriginalFilename()) + " — " + ex.getMessage());
            }
        }

        if (saved.isEmpty()) {
            throw new IllegalArgumentException(rejected.isEmpty()
                    ? "Nothing was uploaded — every file was empty"
                    : String.join("; ", rejected));
        }
        auditService.log(MODULE, "UPLOAD_DOCUMENT", "attachments",
                saved.size() + " file(s) uploaded" + (label == null ? "" : " against " + label));
        return new UploadResult(saved, rejected, duplicates);
    }

    // ----- Edit, link, archive, delete -----

    @Transactional
    public Attachment update(Long id, DocumentEditForm form) {
        Attachment attachment = attachmentRepository.findById(id).orElseThrow();
        DocumentLinkType linkType = parseLinkTypeOrNone(form.linkType());
        Long linkId = linkType.isAttached() ? form.linkId() : null;
        String label = null;
        if (linkType.isAttached()) {
            if (linkId == null) {
                throw new IllegalArgumentException("Choose which record this file belongs to");
            }
            label = labelFor(linkType, linkId);
            if (label == null) {
                throw new IllegalArgumentException("That record no longer exists");
            }
        }
        attachment.setCategory(parseCategoryOrOther(form.category()));
        attachment.setLinkType(linkType);
        attachment.setLinkId(linkId);
        attachment.setLinkLabel(label);
        attachment.setDescription(trimToNull(form.description()));
        attachment.setTags(trimToNull(form.tags()));
        attachment.setTemplate(form.templateValue());
        Attachment saved = attachmentRepository.save(attachment);
        auditService.log(MODULE, "UPDATE_DOCUMENT", "attachment#" + id, saved.getOriginalName());
        return saved;
    }

    @Transactional
    public Attachment detach(Long id) {
        Attachment attachment = attachmentRepository.findById(id).orElseThrow();
        if (!attachment.isAttached()) {
            throw new IllegalStateException("This file is not attached to anything");
        }
        attachment.setLinkType(DocumentLinkType.NONE);
        attachment.setLinkId(null);
        attachment.setLinkLabel(null);
        Attachment saved = attachmentRepository.save(attachment);
        auditService.log(MODULE, "DETACH_DOCUMENT", "attachment#" + id, saved.getOriginalName());
        return saved;
    }

    @Transactional
    public Attachment setArchived(Long id, boolean archived) {
        Attachment attachment = attachmentRepository.findById(id).orElseThrow();
        attachment.setArchived(archived);
        Attachment saved = attachmentRepository.save(attachment);
        auditService.log(MODULE, archived ? "ARCHIVE_DOCUMENT" : "RESTORE_DOCUMENT",
                "attachment#" + id, saved.getOriginalName());
        return saved;
    }

    /**
     * Removes the row and the file. Refused while the document is attached to something, because a
     * receipt behind a posted expense is the evidence for it — detaching first makes that a
     * deliberate act rather than a side effect of tidying up.
     */
    @Transactional
    public void delete(Long id) {
        Attachment attachment = attachmentRepository.findById(id).orElse(null);
        if (attachment == null) {
            return;
        }
        if (attachment.isAttached()) {
            throw new IllegalStateException("This file is attached to " + attachment.getLinkLabel()
                    + ". Detach it first if you really mean to destroy it.");
        }
        boolean removed = storage.delete(attachment.getStorageKey());
        attachmentRepository.delete(attachment);
        auditService.log(MODULE, "DELETE_DOCUMENT", "attachment#" + id,
                attachment.getOriginalName() + (removed ? "" : " (file was already gone)"));
    }

    // ----- Download -----

    /**
     * Hands back the bytes and counts the download. The count is what makes a template's usage
     * visible; on an ordinary document it is simply a record that somebody took a copy.
     */
    @Transactional
    public Download download(Long id) {
        Attachment attachment = attachmentRepository.findById(id).orElse(null);
        if (attachment == null) {
            throw new IllegalArgumentException("That document is not in the library");
        }
        Resource resource = storage.load(attachment.getStorageKey());
        attachment.setDownloadCount(attachment.getDownloadCount() + 1);
        attachmentRepository.save(attachment);
        return new Download(attachment, resource);
    }

    /** Whether the bytes behind a row are actually still there, for the detail page to say so. */
    @Transactional(readOnly = true)
    public boolean fileExists(Attachment attachment) {
        return attachment != null && storage.exists(attachment.getStorageKey());
    }

    // ----- Helpers -----

    /** The upload's filename is display text only, so it is stripped of anything path-like. */
    private static String safeName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "unnamed";
        }
        String name = originalName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            return "unnamed";
        }
        return name.length() > 200 ? name.substring(0, 200) : name;
    }

    public static DocumentCategory parseCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        try {
            return DocumentCategory.valueOf(category.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static DocumentCategory parseCategoryOrOther(String category) {
        DocumentCategory parsed = parseCategory(category);
        return parsed == null ? DocumentCategory.OTHER : parsed;
    }

    public static DocumentLinkType parseLinkType(String linkType) {
        if (linkType == null || linkType.isBlank()) {
            return null;
        }
        try {
            return DocumentLinkType.valueOf(linkType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static DocumentLinkType parseLinkTypeOrNone(String linkType) {
        DocumentLinkType parsed = parseLinkType(linkType);
        return parsed == null ? DocumentLinkType.NONE : parsed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record Choice(Long id, String label) {}

    public record Download(Attachment attachment, Resource resource) {}

    public record UploadResult(List<Attachment> saved, List<String> rejected,
                               List<String> duplicates) {

        public int count() {
            return saved.size();
        }

        public boolean hasDuplicates() {
            return !duplicates.isEmpty();
        }

        public boolean hasRejected() {
            return !rejected.isEmpty();
        }
    }

    public record AttachmentGroup(DocumentLinkType linkType, Long linkId, String label,
                                  String href, List<Attachment> files) {

        public int count() {
            return files.size();
        }
    }

    public record AttachmentView(List<AttachmentGroup> groups, List<Attachment> loose) {

        public boolean isEmpty() {
            return groups.isEmpty() && loose.isEmpty();
        }

        public boolean hasLoose() {
            return !loose.isEmpty();
        }
    }

    public record LibrarySummary(long documents, long unattached, long templates, long archived,
                                 BigDecimal megabytes, String location) {}
}
