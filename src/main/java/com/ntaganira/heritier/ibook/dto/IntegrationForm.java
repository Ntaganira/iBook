/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : IntegrationForm.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : Integration settings form backing bean
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import com.ntaganira.heritier.ibook.entity.Integration;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The settings for one connection.
 *
 * <p>There is no field for a key, and adding one would defeat the point: the form takes the
 * <em>name</em> of the environment variable that holds it. {@code secretEnvVar} is pattern-checked to
 * look like an environment variable name, which also means an actual key pasted into it is rejected
 * rather than quietly written to the database.
 *
 * <p>{@code enabled} is a boxed {@link Boolean} because an unticked checkbox submits nothing at all.
 * A record cannot bind a missing value to a primitive boolean, so Spring would leave the whole form
 * null and the save would appear to work while doing nothing.
 */
public record IntegrationForm(
        @NotBlank(message = "{int.labelRequired}") @Size(max = 120) String label,
        @Size(max = 500) String endpoint,
        @Size(max = 120) String participantId,
        @Size(max = 500) String callbackUrl,
        @Pattern(regexp = "^$|^[A-Za-z_][A-Za-z0-9_]{0,99}$", message = "{int.envVarInvalid}")
        String secretEnvVar,
        String environmentName,
        @Size(max = 300) String contact,
        @Size(max = 1000) String notes,
        Boolean enabled) {

    public boolean enabledValue() {
        return Boolean.TRUE.equals(enabled);
    }

    public static IntegrationForm empty(String label) {
        return new IntegrationForm(label, "", "", "", "", "SANDBOX", "", "", false);
    }

    public static IntegrationForm of(Integration i) {
        return new IntegrationForm(i.getLabel(), i.getEndpoint(), i.getParticipantId(),
                i.getCallbackUrl(), i.getSecretEnvVar(), i.getEnvironmentName(), i.getContact(),
                i.getNotes(), i.isEnabled());
    }
}
