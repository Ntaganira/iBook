/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : AssetDisposalController.java
 * - Date      : 2026. 09. 19.
 * - User      : Hntaganira
 * - Desc      : Asset disposals web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.AssetDisposalForm;
import com.ntaganira.heritier.ibook.entity.AssetDisposal;
import com.ntaganira.heritier.ibook.entity.FixedAsset;
import com.ntaganira.heritier.ibook.enums.AccountType;
import com.ntaganira.heritier.ibook.enums.DisposalMethod;
import com.ntaganira.heritier.ibook.enums.DisposalStatus;
import com.ntaganira.heritier.ibook.service.AccountingService;
import com.ntaganira.heritier.ibook.service.AssetDisposalService;
import com.ntaganira.heritier.ibook.service.AuditService;
import com.ntaganira.heritier.ibook.service.FixedAssetService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/assets/disposals")
public class AssetDisposalController {

    private static final int PAGE_SIZE = 20;

    private final AssetDisposalService disposalService;
    private final FixedAssetService assetService;
    private final AccountingService accountingService;
    private final MessageSource messageSource;

    public AssetDisposalController(AssetDisposalService disposalService,
                                   FixedAssetService assetService,
                                   AccountingService accountingService,
                                   MessageSource messageSource) {
        this.disposalService = disposalService;
        this.assetService = assetService;
        this.accountingService = accountingService;
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

    /** A resolvable error code hides the default message, so the reason goes in as an argument. */
    private void rejectWithReason(BindingResult br, RuntimeException ex) {
        String reason = ex.getMessage() == null ? msg("ast.dis.actionFailed") : ex.getMessage();
        br.reject("ast.dis.failedDetail", new Object[]{reason}, reason);
    }

    private Map<String, String> statusLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (DisposalStatus s : DisposalStatus.values()) {
            m.put(s.name(), msg("ast.dis.status." + s.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private Map<String, String> methodLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (DisposalMethod d : DisposalMethod.values()) {
            m.put(d.name(), msg("ast.dis.method." + d.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private void addFormContext(Model model, String mode, Long editingId,
                                Long assetId, LocalDate disposalDate, BigDecimal proceeds) {
        model.addAttribute("mode", mode);
        model.addAttribute("editingId", editingId);
        model.addAttribute("assets", disposalService.disposableAssets());
        model.addAttribute("methodLabels", methodLabels());
        model.addAttribute("cashAccounts", accountingService.listActiveAccounts().stream()
                .filter(a -> a.getType() == AccountType.ASSET).toList());
        model.addAttribute("revenueAccounts", accountingService.listActiveAccounts().stream()
                .filter(a -> a.getType() == AccountType.REVENUE).toList());
        model.addAttribute("expenseAccounts", accountingService.listActiveAccounts().stream()
                .filter(a -> a.getType() == AccountType.EXPENSE).toList());
        model.addAttribute("accountsMissing", disposalService.gainLossAccountsMissing());
        model.addAttribute("baseCurrency", disposalService.baseCurrency());
        model.addAttribute("nextNumber", disposalService.previewNextNumber());

        FixedAsset asset = assetId == null ? null : assetService.get(assetId);
        model.addAttribute("chosenAsset", asset);
        model.addAttribute("preview", asset == null ? null
                : disposalService.preview(asset, disposalDate, proceeds));
    }

    // ----- List -----

    @GetMapping
    public String disposals(@RequestParam(value = "q", required = false) String q,
                            @RequestParam(value = "status", required = false) String status,
                            @RequestParam(value = "sort", defaultValue = "disposalDate") String sort,
                            @RequestParam(value = "dir", defaultValue = "desc") String dir,
                            @RequestParam(value = "page", defaultValue = "0") int page,
                            Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "disposalDate", "disposalDate", "disposalNo",
                "assetNo", "proceeds", "gainOrLoss", "status");
        model.addAttribute("disposals", disposalService.list(q, status,
                PageRequest.of(page, PAGE_SIZE, sp.sort())));
        model.addAttribute("summary", disposalService.summary());
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("methodLabels", methodLabels());
        model.addAttribute("accountsMissing", disposalService.gainLossAccountsMissing());
        model.addAttribute("baseCurrency", disposalService.baseCurrency());
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
        SortSpec.addListContext(model, "/assets/disposals", fq.isEmpty() ? "" : "?" + fq, sp);
        return "assets/disposals";
    }

    // ----- Create -----

    @GetMapping("/new")
    public String newDisposal(@RequestParam(value = "asset", required = false) Long assetId,
                              @RequestParam(value = "disposalDate", required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate disposalDate,
                              @RequestParam(value = "proceeds", required = false) BigDecimal proceeds,
                              Model model) {
        LocalDate date = disposalDate == null ? LocalDate.now() : disposalDate;
        model.addAttribute("form", new AssetDisposalForm(assetId, date, "SOLD", null, null,
                proceeds == null ? BigDecimal.ZERO : proceeds, null, null, null, null, Boolean.FALSE));
        addFormContext(model, "create", null, assetId, date, proceeds);
        return "assets/disposal-form";
    }

    @PostMapping
    public String create(@ModelAttribute("form") AssetDisposalForm form, BindingResult br,
                         Model model, RedirectAttributes ra) {
        validate(form, br);
        if (br.hasErrors()) {
            addFormContext(model, "create", null, form.assetId(), form.disposalDate(), form.proceeds());
            return "assets/disposal-form";
        }
        try {
            AssetDisposal saved = disposalService.save(form, null, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("ast.dis.saved", saved.getDisposalNo()));
            return "redirect:/assets/disposals/" + saved.getId();
        } catch (RuntimeException ex) {
            rejectWithReason(br, ex);
            addFormContext(model, "create", null, form.assetId(), form.disposalDate(), form.proceeds());
            return "assets/disposal-form";
        }
    }

    private void validate(AssetDisposalForm form, BindingResult br) {
        if (form.assetId() == null) {
            br.rejectValue("assetId", "ast.dis.assetRequired");
        }
        if (form.disposalDate() == null) {
            br.rejectValue("disposalDate", "ast.dis.dateRequired");
        }
        if (form.proceedsValue().signum() < 0) {
            br.rejectValue("proceeds", "ast.dis.proceedsNegative");
        }
        if (form.proceedsValue().signum() > 0 && form.proceedsAccountId() == null) {
            br.rejectValue("proceedsAccountId", "ast.dis.proceedsAccountRequired");
        }
    }

    // ----- View -----

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model, RedirectAttributes ra) {
        AssetDisposal disposal = disposalService.get(id);
        if (disposal == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("ast.dis.notFound", null));
            return "redirect:/assets/disposals";
        }
        model.addAttribute("disposal", disposal);
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("methodLabels", methodLabels());
        model.addAttribute("baseCurrency", disposalService.baseCurrency());
        return "assets/disposal-view";
    }

    // ----- Lifecycle -----

    @PostMapping("/{id}/post")
    public String post(@PathVariable Long id, RedirectAttributes ra) {
        try {
            disposalService.post(id, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("ast.dis.posted", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("ast.dis.actionFailed", ex.getMessage()));
        }
        return "redirect:/assets/disposals/" + id;
    }

    @PostMapping("/{id}/void")
    public String voidDisposal(@PathVariable Long id,
                               @RequestParam(value = "reason", required = false) String reason,
                               RedirectAttributes ra) {
        try {
            disposalService.voidDisposal(id, reason, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("ast.dis.voided", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("ast.dis.actionFailed", ex.getMessage()));
        }
        return "redirect:/assets/disposals/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            disposalService.delete(id);
            ra.addFlashAttribute("flashMessage", flash("ast.dis.deleted", null));
            return "redirect:/assets/disposals";
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("ast.dis.actionFailed", ex.getMessage()));
            return "redirect:/assets/disposals/" + id;
        }
    }
}
