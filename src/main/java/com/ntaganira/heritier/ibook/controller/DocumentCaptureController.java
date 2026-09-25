/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : DocumentCaptureController.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Document capture web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.DocumentExtractionForm;
import com.ntaganira.heritier.ibook.entity.DocumentExtraction;
import com.ntaganira.heritier.ibook.enums.ExtractionStatus;
import com.ntaganira.heritier.ibook.service.AuditService;
import com.ntaganira.heritier.ibook.service.DocumentCaptureService;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/documents/ocr")
public class DocumentCaptureController {

    private static final int PAGE_SIZE = 20;

    private final DocumentCaptureService documentCaptureService;
    private final MessageSource messageSource;

    public DocumentCaptureController(DocumentCaptureService documentCaptureService,
                                     MessageSource messageSource) {
        this.documentCaptureService = documentCaptureService;
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

    private Map<String, String> statusLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (ExtractionStatus s : ExtractionStatus.values()) {
            m.put(s.name(), msg("ocr.status." + s.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private void addReviewContext(Model model, DocumentExtraction extraction) {
        model.addAttribute("extraction", extraction);
        model.addAttribute("attachment", documentCaptureService.attachmentFor(extraction));
        model.addAttribute("expenseAccounts", documentCaptureService.expenseAccounts());
        model.addAttribute("paymentAccounts", documentCaptureService.paymentAccounts());
        model.addAttribute("vendors", documentCaptureService.vendors());
        model.addAttribute("statusLabels", statusLabels());
    }

    // ----- Inbox -----

    @GetMapping
    public String inbox(@RequestParam(value = "q", required = false) String q,
                        @RequestParam(value = "status", required = false) String status,
                        @RequestParam(value = "sort", defaultValue = "createdAt") String sort,
                        @RequestParam(value = "dir", defaultValue = "desc") String dir,
                        @RequestParam(value = "page", defaultValue = "0") int page,
                        Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "createdAt", "createdAt", "supplierName",
                "documentDate", "total", "status");
        model.addAttribute("captures", documentCaptureService.list(q, status,
                PageRequest.of(page, PAGE_SIZE, sp.sort())));
        model.addAttribute("summary", documentCaptureService.summary());
        model.addAttribute("waiting", documentCaptureService.uncaptured());
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("q", q);
        model.addAttribute("status", status == null ? "" : status);

        StringBuilder fq = new StringBuilder();
        if (q != null && !q.isBlank()) {
            fq.append("q=").append(UriUtils.encodeQueryParam(q, StandardCharsets.UTF_8));
        }
        if (status != null && !status.isBlank()) {
            if (!fq.isEmpty()) {
                fq.append('&');
            }
            fq.append("status=").append(status);
        }
        SortSpec.addListContext(model, "/documents/ocr", fq.isEmpty() ? "" : "?" + fq, sp);
        return "documents/ocr";
    }

    @PostMapping("/capture")
    public String capture(@RequestParam("attachmentId") Long attachmentId, RedirectAttributes ra) {
        try {
            DocumentExtraction saved = documentCaptureService.capture(attachmentId,
                    AuditService.currentUsername());
            return "redirect:/documents/ocr/" + saved.getId();
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("ocr.actionFailed", ex.getMessage()));
            return "redirect:/documents/ocr";
        }
    }

    // ----- Review -----

    @GetMapping("/{id}")
    public String review(@PathVariable Long id, Model model, RedirectAttributes ra) {
        DocumentExtraction extraction = documentCaptureService.get(id);
        if (extraction == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("ocr.notFound", null));
            return "redirect:/documents/ocr";
        }
        addReviewContext(model, extraction);
        model.addAttribute("form", documentCaptureService.toForm(extraction));
        return "documents/ocr-review";
    }

    @PostMapping("/{id}")
    public String save(@PathVariable Long id,
                       @Valid @ModelAttribute("form") DocumentExtractionForm form,
                       BindingResult br, Model model, RedirectAttributes ra) {
        DocumentExtraction existing = documentCaptureService.get(id);
        if (existing == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("ocr.notFound", null));
            return "redirect:/documents/ocr";
        }
        if (!br.hasErrors()) {
            try {
                DocumentExtraction saved = documentCaptureService.save(form, id,
                        AuditService.currentUsername());
                if (saved.isConverted()) {
                    ra.addFlashAttribute("flashMessage", flash("ocr.converted", saved.getExpenseNo()));
                    return "redirect:/purchases/expenses/" + saved.getExpenseId();
                }
                ra.addFlashAttribute("flashMessage", flash("ocr.saved", saved.getFileName()));
                return "redirect:/documents/ocr/" + id;
            } catch (RuntimeException ex) {
                String reason = ex.getMessage() == null ? msg("ocr.actionFailed") : ex.getMessage();
                br.reject("ocr.failedDetail", new Object[]{reason}, reason);
            }
        }
        addReviewContext(model, documentCaptureService.get(id));
        return "documents/ocr-review";
    }

    @PostMapping("/{id}/convert")
    public String convert(@PathVariable Long id, RedirectAttributes ra) {
        try {
            DocumentExtraction saved = documentCaptureService.convert(id,
                    AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("ocr.converted", saved.getExpenseNo()));
            return "redirect:/purchases/expenses/" + saved.getExpenseId();
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("ocr.actionFailed", ex.getMessage()));
            return "redirect:/documents/ocr/" + id;
        }
    }

    @PostMapping("/{id}/dismiss")
    public String dismiss(@PathVariable Long id,
                          @RequestParam(value = "reason", required = false) String reason,
                          RedirectAttributes ra) {
        try {
            documentCaptureService.dismiss(id, reason);
            ra.addFlashAttribute("flashMessage", flash("ocr.dismissed", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("ocr.actionFailed", ex.getMessage()));
        }
        return "redirect:/documents/ocr/" + id;
    }

    @PostMapping("/{id}/reopen")
    public String reopen(@PathVariable Long id, RedirectAttributes ra) {
        try {
            documentCaptureService.reopen(id);
            ra.addFlashAttribute("flashMessage", flash("ocr.reopened", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("ocr.actionFailed", ex.getMessage()));
        }
        return "redirect:/documents/ocr/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            documentCaptureService.delete(id);
            ra.addFlashAttribute("flashMessage", flash("ocr.deleted", null));
            return "redirect:/documents/ocr";
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("ocr.actionFailed", ex.getMessage()));
            return "redirect:/documents/ocr/" + id;
        }
    }
}
