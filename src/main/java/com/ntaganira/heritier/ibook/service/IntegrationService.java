/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.service
 * - File      : IntegrationService.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : Recording what an outside connection would need, without connecting
 * </pre>
 */
package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.IntegrationForm;
import com.ntaganira.heritier.ibook.entity.Integration;
import com.ntaganira.heritier.ibook.enums.IntegrationProvider;
import com.ntaganira.heritier.ibook.repository.IntegrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Records the settings an outside connection would need.
 *
 * <p><strong>Nothing in this class connects to anything.</strong> There is no HTTP client here and
 * none anywhere else in the application. That is not an oversight to be worked around later in this
 * service: a real integration is a client, a retry policy, a callback endpoint, signature
 * verification and a reconciliation story for payments that arrive twice or not at all. None of that
 * is a settings screen, and pretending otherwise on this page would be the dangerous part.
 *
 * <p>So what this does is the honest half: it keeps the settings somebody has gathered, says which
 * ones are still missing, and states per provider what stands between the settings and a working
 * connection. When the client is eventually written, it reads its configuration from here and its
 * secrets from the environment.
 *
 * <p><strong>Secrets are not handled at all.</strong> {@link Integration#getSecretEnvVar()} holds the
 * name of an environment variable; the value is never read into a field, returned from a method or
 * put in a model. The one thing reported about it is whether the variable is set, which is a boolean
 * and cannot leak the key.
 */
@Service
public class IntegrationService {

    private final IntegrationRepository integrationRepository;

    public IntegrationService(IntegrationRepository integrationRepository) {
        this.integrationRepository = integrationRepository;
    }

    @Transactional(readOnly = true)
    public Registry registry(IntegrationProvider provider) {
        List<Integration> rows = integrationRepository.findByProviderOrderByLabelAsc(provider);
        List<Entry> entries = new ArrayList<>();
        for (Integration row : rows) {
            entries.add(new Entry(row, missingFor(row), row.isSecretPresent()));
        }
        return new Registry(provider, entries);
    }

    /**
     * Which required settings have not been filled in, as field names the page turns into labels.
     *
     * <p>This is the only "check" offered, and it is local: it inspects the recorded fields. It
     * cannot say whether an endpoint answers, whether a merchant code is registered or whether a key
     * is the right one, and the page does not claim it can.
     */
    private List<String> missingFor(Integration row) {
        List<String> missing = new ArrayList<>();
        IntegrationProvider p = row.getProvider();
        if (p.needsEndpoint() && isBlank(row.getEndpoint())) {
            missing.add("endpoint");
        }
        if (p.needsParticipantId() && isBlank(row.getParticipantId())) {
            missing.add("participantId");
        }
        if (p.needsCallback() && isBlank(row.getCallbackUrl())) {
            missing.add("callbackUrl");
        }
        if (isBlank(row.getSecretEnvVar())) {
            missing.add("secretEnvVar");
        }
        return missing;
    }

    @Transactional
    public Integration save(IntegrationProvider provider, Long id, IntegrationForm form, String user) {
        if (!provider.isConfigurable()) {
            throw new IllegalStateException("There is nothing to configure here. " + provider
                    + " has no settings because the thing it would configure does not exist yet, and "
                    + "recording settings for it would only create the impression that it did.");
        }
        String label = form.label() == null ? "" : form.label().trim();

        Integration row;
        if (id == null) {
            if (!provider.allowsMany() && !integrationRepository
                    .findByProviderOrderByLabelAsc(provider).isEmpty()) {
                throw new IllegalStateException("There is already a configuration for this provider. "
                        + "Only webhooks can have more than one, because only they have more than one "
                        + "destination — edit the existing one instead of adding a second that would "
                        + "leave nobody able to say which was in force.");
            }
            if (integrationRepository.existsByProviderAndLabelIgnoreCase(provider, label)) {
                throw new IllegalStateException("A configuration named " + label + " already exists.");
            }
            row = Integration.builder().provider(provider).build();
        } else {
            row = integrationRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Configuration not found: " + id));
            if (row.getProvider() != provider) {
                throw new IllegalArgumentException("That configuration belongs to another provider.");
            }
            if (integrationRepository.existsByProviderAndLabelIgnoreCaseAndIdNot(provider, label, id)) {
                throw new IllegalStateException("A configuration named " + label + " already exists.");
            }
        }

        row.setLabel(label);
        row.setEndpoint(trimToNull(form.endpoint()));
        row.setParticipantId(trimToNull(form.participantId()));
        row.setCallbackUrl(trimToNull(form.callbackUrl()));
        row.setSecretEnvVar(trimToNull(form.secretEnvVar()));
        row.setEnvironmentName(provider.hasEnvironments()
                ? (isBlank(form.environmentName()) ? "SANDBOX" : form.environmentName())
                : null);
        row.setContact(trimToNull(form.contact()));
        row.setNotes(trimToNull(form.notes()));
        row.setEnabled(form.enabledValue());
        row.setUpdatedBy(user);
        return integrationRepository.save(row);
    }

    @Transactional
    public void delete(IntegrationProvider provider, Long id) {
        Integration row = integrationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Configuration not found: " + id));
        if (row.getProvider() != provider) {
            throw new IllegalArgumentException("That configuration belongs to another provider.");
        }
        integrationRepository.delete(row);
    }

    /** One configuration, with what is still missing from it. */
    public record Entry(Integration row, List<String> missing, boolean secretPresent) {

        public boolean isComplete() {
            return missing.isEmpty();
        }

        /**
         * Settings recorded and switched on — which is <em>not</em> a working connection, and the
         * pages never render this as one. It means only that somebody finished filling the form in.
         */
        public boolean isReady() {
            return missing.isEmpty() && row.isEnabled() && secretPresent;
        }
    }

    public record Registry(IntegrationProvider provider, List<Entry> entries) {

        public boolean hasAny() {
            return !entries.isEmpty();
        }

        /** The single configuration, for the six providers that can only have one. */
        public Entry single() {
            return entries.isEmpty() ? null : entries.get(0);
        }

        public boolean canAdd() {
            return provider.allowsMany() || entries.isEmpty();
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String trimToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }
}
