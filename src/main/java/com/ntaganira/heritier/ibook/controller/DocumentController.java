/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : DocumentController.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Document library web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.DocumentEditForm;
import com.ntaganira.heritier.ibook.dto.DocumentUploadForm;
import com.ntaganira.heritier.ibook.entity.Attachment;
import com.ntaganira.heritier.ibook.enums.DocumentCategory;
import com.ntaganira.heritier.ibook.enums.DocumentLinkType;
import com.ntaganira.heritier.ibook.service.AuditService;
import com.ntaganira.heritier.ibook.service.DocumentService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/documents")
public class DocumentController {

    private static final int PAGE_SIZE = 20;

    private final DocumentService documentService;
    private final MessageSource messageSource;

    public DocumentController(DocumentService documentService, MessageSource messageSource) {
        this.documentService = documentService;
        this.messageSource = messageSource;
    }

    private String msg(String key) {
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
    }

    private Map<String, String> flash(String key, String detail) {
        return Map.of("title", msg(key), "detail", detail == null ? "" : detail, "type", "success");
    }

    private Map<String, String> errorFlash(String key, String detail) {
        return Map.of("title", msg(key), "detail", detail == null ? "" : detail, "type", "error");
    }

    private Map<String, String> categoryLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (DocumentCategory c : DocumentCategory.values()) {
            m.put(c.name(), msg("doc.category." + c.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private Map<String, String> linkTypeLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (DocumentLinkType t : DocumentLinkType.values()) {
            m.put(t.name(), msg("doc.link." + t.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private void addSharedContext(Model model) {
        model.addAttribute("categoryLabels", categoryLabels());
        model.addAttribute("linkTypeLabels", linkTypeLabels());
        model.addAttribute("summary", documentService.summary());
    }

    // ----- Library -----

    @GetMapping
    public String documents(@RequestParam(value = "q", required = false) String q,
                            @RequestParam(value = "category", required = false) String category,
                            @RequestParam(value = "link", required = false) String linkType,
                            @RequestParam(value = "archived", defaultValue = "false") boolean archived,
                            @RequestParam(value = "sort", defaultValue = "createdAt") String sort,
                            @RequestParam(value = "dir", defaultValue = "desc") String dir,
                            @RequestParam(value = "page", defaultValue = "0") int page,
                            Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "createdAt", "createdAt", "originalName",
                "category", "sizeBytes", "linkLabel");
        model.addAttribute("documents", documentService.list(q, category, linkType, false,
                archived, PageRequest.of(page, PAGE_SIZE, sp.sort())));
        addSharedContext(model);
        model.addAttribute("q", q);
        model.addAttribute("category", category == null ? "" : category);
        model.addAttribute("linkType", linkType == null ? "" : linkType);
        model.addAttribute("archived", archived);

        StringBuilder fq = new StringBuilder();
        if (q != null && !q.isBlank()) {
            fq.append("q=").append(UriUtils.encodeQueryParam(q, StandardCharsets.UTF_8));
        }
        if (category != null && !category.isBlank()) {
            appendTo(fq, "category=" + category);
        }
        if (linkType != null && !linkType.isBlank()) {
            appendTo(fq, "link=" + linkType);
        }
        if (archived) {
            appendTo(fq, "archived=true");
        }
        SortSpec.addListContext(model, "/documents", fq.isEmpty() ? "" : "?" + fq, sp);
        return "documents/documents";
    }

    private static void appendTo(StringBuilder fq, String part) {
        if (!fq.isEmpty()) {
            fq.append('&');
        }
        fq.append(part);
    }

    // ----- Upload -----

    @GetMapping("/upload")
    public String uploadForm(@RequestParam(value = "link", required = false) String linkType,
                             @RequestParam(value = "id", required = false) Long linkId,
                             Model model) {
        DocumentLinkType type = DocumentService.parseLinkType(linkType);
        addSharedContext(model);
        model.addAttribute("form", new DocumentUploadForm("OTHER",
                type == null ? "NONE" : type.name(), linkId, null, null, Boolean.FALSE));
        model.addAttribute("choices", documentService.choicesFor(type));
        model.addAttribute("chosenType", type == null ? "NONE" : type.name());
        return "documents/upload";
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("files") MultipartFile[] files,
                         @ModelAttribute DocumentUploadForm form,
                         Model model, RedirectAttributes ra) {
        try {
            DocumentService.UploadResult result =
                    documentService.upload(files, form, AuditService.currentUsername());
            String detail = messageSource.getMessage("doc.uploadedDetail",
                    new Object[]{result.count()}, LocaleContextHolder.getLocale());
            if (result.hasDuplicates()) {
                detail = detail + " · " + messageSource.getMessage("doc.duplicateWarning",
                        new Object[]{String.join(", ", result.duplicates())},
                        LocaleContextHolder.getLocale());
            }
            if (result.hasRejected()) {
                detail = detail + " · " + String.join("; ", result.rejected());
            }
            ra.addFlashAttribute("flashMessage", flash("doc.uploaded", detail));
            return "redirect:/documents";
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("doc.uploadFailed", ex.getMessage()));
            return "redirect:/documents/upload";
        }
    }

    // ----- Attachments -----

    @GetMapping("/attachments")
    public String attachments(Model model) {
        addSharedContext(model);
        model.addAttribute("view", documentService.attachmentView());
        return "documents/attachments";
    }

    // ----- Templates -----

    @GetMapping("/templates")
    public String templates(@RequestParam(value = "q", required = false) String q,
                            @RequestParam(value = "category", required = false) String category,
                            @RequestParam(value = "sort", defaultValue = "originalName") String sort,
                            @RequestParam(value = "dir", defaultValue = "asc") String dir,
                            @RequestParam(value = "page", defaultValue = "0") int page,
                            Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "originalName", "originalName", "category",
                "downloadCount", "createdAt");
        model.addAttribute("documents", documentService.list(q, category, null, true, false,
                PageRequest.of(page, PAGE_SIZE, sp.sort())));
        addSharedContext(model);
        model.addAttribute("q", q);
        model.addAttribute("category", category == null ? "" : category);

        StringBuilder fq = new StringBuilder();
        if (q != null && !q.isBlank()) {
            fq.append("q=").append(UriUtils.encodeQueryParam(q, StandardCharsets.UTF_8));
        }
        if (category != null && !category.isBlank()) {
            appendTo(fq, "category=" + category);
        }
        SortSpec.addListContext(model, "/documents/templates", fq.isEmpty() ? "" : "?" + fq, sp);
        return "documents/templates";
    }

    // ----- One document -----

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model, RedirectAttributes ra) {
        Attachment attachment = documentService.get(id);
        if (attachment == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("doc.notFound", null));
            return "redirect:/documents";
        }
        addSharedContext(model);
        model.addAttribute("document", attachment);
        model.addAttribute("fileExists", documentService.fileExists(attachment));
        model.addAttribute("choices", documentService.choicesFor(attachment.getLinkType()));
        model.addAttribute("form", new DocumentEditForm(attachment.getCategory().name(),
                attachment.getLinkType().name(), attachment.getLinkId(),
                attachment.getDescription(), attachment.getTags(), attachment.isTemplate()));
        return "documents/document-view";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute DocumentEditForm form,
                         RedirectAttributes ra) {
        try {
            documentService.update(id, form);
            ra.addFlashAttribute("flashMessage", flash("doc.saved", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("doc.actionFailed", ex.getMessage()));
        }
        return "redirect:/documents/" + id;
    }

    @PostMapping("/{id}/detach")
    public String detach(@PathVariable Long id, RedirectAttributes ra) {
        try {
            documentService.detach(id);
            ra.addFlashAttribute("flashMessage", flash("doc.detached", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("doc.actionFailed", ex.getMessage()));
        }
        return "redirect:/documents/" + id;
    }

    @PostMapping("/{id}/archive")
    public String archive(@PathVariable Long id, RedirectAttributes ra) {
        try {
            documentService.setArchived(id, true);
            ra.addFlashAttribute("flashMessage", flash("doc.archived", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("doc.actionFailed", ex.getMessage()));
        }
        return "redirect:/documents/" + id;
    }

    @PostMapping("/{id}/restore")
    public String restore(@PathVariable Long id, RedirectAttributes ra) {
        try {
            documentService.setArchived(id, false);
            ra.addFlashAttribute("flashMessage", flash("doc.restored", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("doc.actionFailed", ex.getMessage()));
        }
        return "redirect:/documents/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            documentService.delete(id);
            ra.addFlashAttribute("flashMessage", flash("doc.deleted", null));
            return "redirect:/documents";
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("doc.actionFailed", ex.getMessage()));
            return "redirect:/documents/" + id;
        }
    }

    // ----- Download -----

    /**
     * Hands the file over.
     *
     * <p>Everything not on the inline allow list is served as {@code application/octet-stream} with
     * {@code Content-Disposition: attachment}. An uploaded HTML or SVG file served inline would run
     * its own script on this application's origin with this application's session cookie, so the
     * safe default is to download rather than display, and the filename is quoted and escaped so it
     * cannot break out of the header.
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id,
                                             @RequestParam(value = "inline", defaultValue = "false")
                                             boolean inline) {
        DocumentService.Download download;
        try {
            download = documentService.download(id);
        } catch (RuntimeException ex) {
            return ResponseEntity.notFound().build();
        }
        Attachment attachment = download.attachment();
        boolean showInline = inline && attachment.isViewableInline();
        MediaType type = showInline && attachment.getContentType() != null
                ? MediaType.parseMediaType(attachment.getContentType())
                : MediaType.APPLICATION_OCTET_STREAM;

        String ascii = attachment.getOriginalName().replaceAll("[^A-Za-z0-9._-]", "_");
        String encoded = UriUtils.encode(attachment.getOriginalName(), StandardCharsets.UTF_8);
        String disposition = (showInline ? "inline" : "attachment")
                + "; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;

        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=0, must-revalidate")
                .body(download.resource());
    }
}
