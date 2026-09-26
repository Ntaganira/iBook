/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : IntegrationController.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : Integration settings web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.IntegrationForm;
import com.ntaganira.heritier.ibook.enums.IntegrationProvider;
import com.ntaganira.heritier.ibook.service.AuditService;
import com.ntaganira.heritier.ibook.service.IntegrationService;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Serves all seven {@code /integrations/*} routes from one place.
 *
 * <p>One controller rather than seven, because the pages differ only in which fields a provider needs
 * and what is still missing before it could work — both of which {@link IntegrationProvider} already
 * knows. Seven near-identical controllers would drift apart, and the first one to drift would be the
 * one whose "nothing is transmitted" warning somebody quietly dropped.
 */
@Controller
@RequestMapping("/integrations")
public class IntegrationController {

    private final IntegrationService integrationService;
    private final MessageSource messageSource;

    public IntegrationController(IntegrationService integrationService, MessageSource messageSource) {
        this.integrationService = integrationService;
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

    private IntegrationProvider resolve(String slug) {
        IntegrationProvider provider = IntegrationProvider.fromSlug(slug);
        if (provider == null) {
            throw new ResponseStatusException(NOT_FOUND, "No such integration: " + slug);
        }
        return provider;
    }

    /**
     * Puts everything the page needs on the model. The provider's key is lower-cased into a message
     * key so the page's own heading, description and per-provider wording come out of the bundles
     * rather than out of the enum, which holds the English engineering note only.
     */
    private void populate(IntegrationProvider provider, Model model) {
        String key = provider.name().toLowerCase(Locale.ROOT);
        model.addAttribute("provider", provider);
        model.addAttribute("slug", provider.slug());
        model.addAttribute("registry", integrationService.registry(provider));
        model.addAttribute("pageTitle", msg("int." + key + ".title"));
        model.addAttribute("pageSubtitle", msg("int." + key + ".subtitle"));
        model.addAttribute("whatItWouldTake", msg("int." + key + ".missing"));
        model.addAttribute("participantLabel", msg("int." + key + ".participant"));
        model.addAttribute("endpointLabel", msg("int." + key + ".endpoint"));
    }

    @GetMapping("/{slug}")
    public String settings(@PathVariable String slug,
                           @RequestParam(value = "edit", required = false) Long edit,
                           @RequestParam(value = "add", required = false) Boolean add,
                           Model model) {
        IntegrationProvider provider = resolve(slug);
        populate(provider, model);

        IntegrationService.Registry registry = integrationService.registry(provider);
        if (!provider.isConfigurable()) {
            model.addAttribute("form", null);
            return "integrations/settings";
        }
        if (edit != null) {
            registry.entries().stream()
                    .filter(e -> edit.equals(e.row().getId()))
                    .findFirst()
                    .ifPresent(e -> {
                        model.addAttribute("form", IntegrationForm.of(e.row()));
                        model.addAttribute("editingId", edit);
                    });
        } else if (Boolean.TRUE.equals(add) || (!registry.hasAny() && !provider.allowsMany())) {
            model.addAttribute("form", IntegrationForm.empty(msg("int." + provider.name()
                    .toLowerCase(Locale.ROOT) + ".defaultLabel")));
        }
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", null);
        }
        return "integrations/settings";
    }

    @PostMapping("/{slug}/save")
    public String save(@PathVariable String slug,
                       @RequestParam(value = "id", required = false) Long id,
                       @Valid @ModelAttribute("form") IntegrationForm form,
                       BindingResult binding,
                       Model model,
                       RedirectAttributes ra) {
        IntegrationProvider provider = resolve(slug);
        if (binding.hasErrors()) {
            populate(provider, model);
            model.addAttribute("editingId", id);
            return "integrations/settings";
        }
        try {
            integrationService.save(provider, id, form, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("int.saved", form.label()));
        } catch (IllegalStateException | IllegalArgumentException e) {
            ra.addFlashAttribute("flashMessage", errorFlash("int.saveFailed", e.getMessage()));
        }
        return "redirect:/integrations/" + provider.slug();
    }

    @PostMapping("/{slug}/{id}/delete")
    public String delete(@PathVariable String slug, @PathVariable Long id, RedirectAttributes ra) {
        IntegrationProvider provider = resolve(slug);
        try {
            integrationService.delete(provider, id);
            ra.addFlashAttribute("flashMessage", flash("int.deleted", null));
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flashMessage", errorFlash("int.saveFailed", e.getMessage()));
        }
        return "redirect:/integrations/" + provider.slug();
    }
}
