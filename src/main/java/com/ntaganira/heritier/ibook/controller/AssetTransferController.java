/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : AssetTransferController.java
 * - Date      : 2026. 09. 19.
 * - User      : Hntaganira
 * - Desc      : Asset transfers web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.AssetTransferForm;
import com.ntaganira.heritier.ibook.entity.AssetTransfer;
import com.ntaganira.heritier.ibook.entity.FixedAsset;
import com.ntaganira.heritier.ibook.enums.AccountType;
import com.ntaganira.heritier.ibook.enums.AssetTransferStatus;
import com.ntaganira.heritier.ibook.service.AccountingService;
import com.ntaganira.heritier.ibook.service.AssetTransferService;
import com.ntaganira.heritier.ibook.service.AuditService;
import com.ntaganira.heritier.ibook.service.FixedAssetService;
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
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/assets/transfers")
public class AssetTransferController {

    private static final int PAGE_SIZE = 20;

    private final AssetTransferService transferService;
    private final FixedAssetService assetService;
    private final AccountingService accountingService;
    private final MessageSource messageSource;

    public AssetTransferController(AssetTransferService transferService,
                                   FixedAssetService assetService,
                                   AccountingService accountingService,
                                   MessageSource messageSource) {
        this.transferService = transferService;
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
        String reason = ex.getMessage() == null ? msg("ast.trf.actionFailed") : ex.getMessage();
        br.reject("ast.trf.failedDetail", new Object[]{reason}, reason);
    }

    private Map<String, String> statusLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (AssetTransferStatus s : AssetTransferStatus.values()) {
            m.put(s.name(), msg("ast.trf.status." + s.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private void addFormContext(Model model, String mode, Long editingId, Long assetId) {
        model.addAttribute("mode", mode);
        model.addAttribute("editingId", editingId);
        model.addAttribute("assets", transferService.movableAssets());
        model.addAttribute("locations", transferService.knownLocations());
        model.addAttribute("assetAccounts", accountingService.listActiveAccounts().stream()
                .filter(a -> a.getType() == AccountType.ASSET).toList());
        model.addAttribute("baseCurrency", transferService.baseCurrency());
        model.addAttribute("nextNumber", transferService.previewNextNumber());
        model.addAttribute("chosenAsset", assetId == null ? null : assetService.get(assetId));
    }

    // ----- List -----

    @GetMapping
    public String transfers(@RequestParam(value = "q", required = false) String q,
                            @RequestParam(value = "status", required = false) String status,
                            @RequestParam(value = "sort", defaultValue = "transferDate") String sort,
                            @RequestParam(value = "dir", defaultValue = "desc") String dir,
                            @RequestParam(value = "page", defaultValue = "0") int page,
                            Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "transferDate", "transferDate", "transferNo",
                "assetNo", "toLocation", "status");
        model.addAttribute("transfers", transferService.list(q, status,
                PageRequest.of(page, PAGE_SIZE, sp.sort())));
        model.addAttribute("summary", transferService.summary());
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("baseCurrency", transferService.baseCurrency());
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
        SortSpec.addListContext(model, "/assets/transfers", fq.isEmpty() ? "" : "?" + fq, sp);
        return "assets/transfers";
    }

    // ----- Create -----

    @GetMapping("/new")
    public String newTransfer(@RequestParam(value = "asset", required = false) Long assetId,
                              Model model) {
        FixedAsset asset = assetId == null ? null : assetService.get(assetId);
        model.addAttribute("form", new AssetTransferForm(assetId, LocalDate.now(), null, null,
                asset == null ? null : asset.getAssetAccountId(),
                asset == null ? null : asset.getAccumulatedAccountId(),
                null, null, null, Boolean.FALSE));
        addFormContext(model, "create", null, assetId);
        return "assets/transfer-form";
    }

    @PostMapping
    public String create(@ModelAttribute("form") AssetTransferForm form, BindingResult br,
                         Model model, RedirectAttributes ra) {
        validate(form, br);
        if (br.hasErrors()) {
            addFormContext(model, "create", null, form.assetId());
            return "assets/transfer-form";
        }
        try {
            AssetTransfer saved = transferService.save(form, null, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("ast.trf.saved", saved.getTransferNo()));
            return "redirect:/assets/transfers/" + saved.getId();
        } catch (RuntimeException ex) {
            rejectWithReason(br, ex);
            addFormContext(model, "create", null, form.assetId());
            return "assets/transfer-form";
        }
    }

    private void validate(AssetTransferForm form, BindingResult br) {
        if (form.assetId() == null) {
            br.rejectValue("assetId", "ast.trf.assetRequired");
        }
        if (form.transferDate() == null) {
            br.rejectValue("transferDate", "ast.trf.dateRequired");
        }
    }

    // ----- View -----

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model, RedirectAttributes ra) {
        AssetTransfer transfer = transferService.get(id);
        if (transfer == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("ast.trf.notFound", null));
            return "redirect:/assets/transfers";
        }
        model.addAttribute("transfer", transfer);
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("baseCurrency", transferService.baseCurrency());
        model.addAttribute("history", transferService.historyFor(transfer.getAssetId()));
        return "assets/transfer-view";
    }

    // ----- Lifecycle -----

    @PostMapping("/{id}/complete")
    public String complete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            transferService.complete(id, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("ast.trf.completed", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("ast.trf.actionFailed", ex.getMessage()));
        }
        return "redirect:/assets/transfers/" + id;
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id,
                         @RequestParam(value = "reason", required = false) String reason,
                         RedirectAttributes ra) {
        try {
            transferService.cancel(id, reason);
            ra.addFlashAttribute("flashMessage", flash("ast.trf.cancelled", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("ast.trf.actionFailed", ex.getMessage()));
        }
        return "redirect:/assets/transfers/" + id;
    }

    @PostMapping("/{id}/void")
    public String voidTransfer(@PathVariable Long id,
                               @RequestParam(value = "reason", required = false) String reason,
                               RedirectAttributes ra) {
        try {
            transferService.voidTransfer(id, reason, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("ast.trf.voided", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("ast.trf.actionFailed", ex.getMessage()));
        }
        return "redirect:/assets/transfers/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            transferService.delete(id);
            ra.addFlashAttribute("flashMessage", flash("ast.trf.deleted", null));
            return "redirect:/assets/transfers";
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("ast.trf.actionFailed", ex.getMessage()));
            return "redirect:/assets/transfers/" + id;
        }
    }
}
