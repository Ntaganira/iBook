/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : BankFeedController.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : Statement import and the uncategorised worklist
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.entity.BankFeedImport;
import com.ntaganira.heritier.ibook.enums.FeedLineStatus;
import com.ntaganira.heritier.ibook.service.AuditService;
import com.ntaganira.heritier.ibook.service.BankFeedService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;
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

/**
 * Serves {@code /banking/feeds} — bringing a statement file in — and
 * {@code /banking/uncategorized}, the worklist of lines nobody has yet said anything about.
 */
@Controller
public class BankFeedController {

    private static final int PAGE_SIZE = 25;

    private final BankFeedService bankFeedService;
    private final MessageSource messageSource;

    public BankFeedController(BankFeedService bankFeedService, MessageSource messageSource) {
        this.bankFeedService = bankFeedService;
        this.messageSource = messageSource;
    }

    private String msg(String key) {
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
    }

    private Map<String, String> flash(String key, String detail) {
        return Map.of("title", msg(key), "detail", detail == null ? "" : detail, "type", "success");
    }

    private Map<String, String> warnFlash(String key, String detail) {
        return Map.of("title", msg(key), "detail", detail == null ? "" : detail, "type", "warning");
    }

    private Map<String, String> errorFlash(String key, String detail) {
        return Map.of("title", msg(key), "detail", detail == null ? "" : detail, "type", "error");
    }

    private Map<String, String> statusLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (FeedLineStatus s : FeedLineStatus.values()) {
            m.put(s.name(), msg("fed.status." + s.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    // ----- Imports -----

    @GetMapping("/banking/feeds")
    public String feeds(@RequestParam(value = "q", required = false) String q,
                        @RequestParam(value = "accountId", required = false) Long accountId,
                        @RequestParam(value = "sort", defaultValue = "importedAt") String sort,
                        @RequestParam(value = "dir", defaultValue = "desc") String dir,
                        @RequestParam(value = "page", defaultValue = "0") int page,
                        Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "importedAt", "importedAt", "reference",
                "accountCode", "lineCount");
        model.addAttribute("imports", bankFeedService.imports(q, accountId,
                PageRequest.of(page, PAGE_SIZE, sp.sort())));
        model.addAttribute("summary", bankFeedService.summary());
        model.addAttribute("bankAccounts", bankFeedService.bankAccounts());
        model.addAttribute("q", q);
        model.addAttribute("accountId", accountId);

        StringBuilder fq = new StringBuilder();
        if (q != null && !q.isBlank()) {
            fq.append("q=").append(UriUtils.encodeQueryParam(q, StandardCharsets.UTF_8));
        }
        if (accountId != null) {
            if (!fq.isEmpty()) {
                fq.append('&');
            }
            fq.append("accountId=").append(accountId);
        }
        SortSpec.addListContext(model, "/banking/feeds", fq.isEmpty() ? "" : "?" + fq, sp);
        return "banking/feeds";
    }

    @PostMapping("/banking/feeds")
    public String importFile(@RequestParam("accountId") Long accountId,
                             @RequestParam("file") MultipartFile file,
                             RedirectAttributes ra) {
        try {
            BankFeedService.ImportResult result = bankFeedService.importFile(accountId, file,
                    AuditService.currentUsername());
            StringBuilder detail = new StringBuilder();
            detail.append(msg("fed.importedCount")).append(' ')
                    .append(result.feed().getLineCount());
            if (result.hasRejected()) {
                detail.append(" · ").append(result.rejected().size()).append(' ')
                        .append(msg("fed.rejectedCount"));
            }
            if (result.isDuplicate()) {
                // A warning rather than a refusal: sometimes the same file genuinely is re-sent,
                // and refusing outright would leave somebody unable to proceed at all.
                ra.addFlashAttribute("flashMessage", warnFlash("fed.duplicateTitle",
                        msg("fed.duplicateDetail") + " " + result.duplicatesOf().get(0).getReference()
                                + " · " + detail));
            } else {
                ra.addFlashAttribute("flashMessage", flash("fed.imported", detail.toString()));
            }
            return "redirect:/banking/feeds/" + result.feed().getId();
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("fed.actionFailed", ex.getMessage()));
            return "redirect:/banking/feeds";
        }
    }

    @GetMapping("/banking/feeds/{id}")
    public String viewImport(@PathVariable Long id, Model model, RedirectAttributes ra) {
        BankFeedImport feed = bankFeedService.getImport(id);
        if (feed == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("fed.notFound", null));
            return "redirect:/banking/feeds";
        }
        model.addAttribute("feed", feed);
        model.addAttribute("lines", bankFeedService.linesOf(id));
        model.addAttribute("categoryAccounts", bankFeedService.categoryAccounts());
        model.addAttribute("statusLabels", statusLabels());
        return "banking/feed-view";
    }

    @PostMapping("/banking/feeds/{id}/discard")
    public String discard(@PathVariable Long id,
                          @RequestParam(value = "reason", required = false) String reason,
                          RedirectAttributes ra) {
        try {
            bankFeedService.discard(id, reason, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("fed.discarded", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("fed.actionFailed", ex.getMessage()));
        }
        return "redirect:/banking/feeds/" + id;
    }

    @PostMapping("/banking/feeds/{id}/delete")
    public String deleteImport(@PathVariable Long id, RedirectAttributes ra) {
        try {
            bankFeedService.delete(id);
            ra.addFlashAttribute("flashMessage", flash("fed.deleted", null));
            return "redirect:/banking/feeds";
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("fed.actionFailed", ex.getMessage()));
            return "redirect:/banking/feeds/" + id;
        }
    }

    // ----- The worklist -----

    @GetMapping("/banking/uncategorized")
    public String uncategorised(@RequestParam(value = "q", required = false) String q,
                                @RequestParam(value = "accountId", required = false) Long accountId,
                                @RequestParam(value = "status", defaultValue = "UNCATEGORISED") String status,
                                @RequestParam(value = "sort", defaultValue = "lineDate") String sort,
                                @RequestParam(value = "dir", defaultValue = "asc") String dir,
                                @RequestParam(value = "page", defaultValue = "0") int page,
                                Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "lineDate", "lineDate", "moneyIn", "moneyOut",
                "status");
        model.addAttribute("lines", bankFeedService.lines(q, accountId, status,
                PageRequest.of(page, PAGE_SIZE, sp.sort())));
        model.addAttribute("summary", bankFeedService.summary());
        model.addAttribute("bankAccounts", bankFeedService.bankAccounts());
        model.addAttribute("categoryAccounts", bankFeedService.categoryAccounts());
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("q", q);
        model.addAttribute("accountId", accountId);
        model.addAttribute("status", status);

        StringBuilder fq = new StringBuilder();
        fq.append("status=").append(status);
        if (q != null && !q.isBlank()) {
            fq.append("&q=").append(UriUtils.encodeQueryParam(q, StandardCharsets.UTF_8));
        }
        if (accountId != null) {
            fq.append("&accountId=").append(accountId);
        }
        SortSpec.addListContext(model, "/banking/uncategorized", "?" + fq, sp);
        return "banking/uncategorized";
    }

    @PostMapping("/banking/uncategorized/{id}/categorise")
    public String categorise(@PathVariable Long id,
                             @RequestParam("categoryAccountId") Long categoryAccountId,
                             @RequestParam(value = "note", required = false) String note,
                             @RequestParam(value = "back", required = false) String back,
                             RedirectAttributes ra) {
        try {
            bankFeedService.categorise(id, categoryAccountId, note, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("fed.categorised", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("fed.actionFailed", ex.getMessage()));
        }
        return "redirect:" + safeBack(back);
    }

    @PostMapping("/banking/uncategorized/{id}/ignore")
    public String ignore(@PathVariable Long id,
                         @RequestParam(value = "note", required = false) String note,
                         @RequestParam(value = "back", required = false) String back,
                         RedirectAttributes ra) {
        try {
            bankFeedService.ignore(id, note, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("fed.ignored", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("fed.actionFailed", ex.getMessage()));
        }
        return "redirect:" + safeBack(back);
    }

    @PostMapping("/banking/uncategorized/{id}/reopen")
    public String reopen(@PathVariable Long id,
                         @RequestParam(value = "back", required = false) String back,
                         RedirectAttributes ra) {
        try {
            bankFeedService.reopen(id);
            ra.addFlashAttribute("flashMessage", flash("fed.reopened", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("fed.actionFailed", ex.getMessage()));
        }
        return "redirect:" + safeBack(back);
    }

    /** Only a path inside banking is honoured, so a crafted parameter cannot bounce somebody away. */
    private static String safeBack(String back) {
        return back != null && back.startsWith("/banking/") ? back : "/banking/uncategorized";
    }
}
