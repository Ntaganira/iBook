/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : Integration.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : Settings an outside connection would need, recorded without its secret
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.IntegrationProvider;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * The settings one connection to an outside service would need.
 *
 * <p><strong>No secret is stored here, and there is no column one could be stored in.</strong> What
 * is stored is {@link #secretEnvVar} — the <em>name</em> of the environment variable that holds the
 * key, such as {@code MOMO_API_KEY}. The name is not sensitive; the value never reaches the
 * database, the backups, the audit trail or a page.
 *
 * <p>That is a deliberate choice and it costs something: somebody has to set the variable on the
 * server, and this application cannot tell them whether they got it right, because it never reads
 * it. The alternative — a password field on a form, written to a column — would put live payment
 * credentials in plaintext in a database that is dumped to backups and read by anybody with the
 * connection string already committed to this repository. A recorded setting is worth having. A
 * recorded secret is a liability, and encrypting it would only move the problem to wherever the
 * encryption key then lived.
 *
 * <p>{@link #status} says whether the settings have been filled in. It never says "connected",
 * because nothing connects: there is no HTTP client, no polling and no callback handler anywhere in
 * this codebase. {@code CONFIGURED} means somebody wrote the settings down, and the pages say so in
 * those words.
 */
@Entity
@Table(name = "integrations")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Integration implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private IntegrationProvider provider;

    /** What this one is for. Only webhooks have more than one, so only they really need a label. */
    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "endpoint", length = 500)
    private String endpoint;

    /**
     * The merchant code, TIN, participant id or subscription key — whichever the provider calls the
     * thing that identifies the account. Not a secret: it appears on receipts and in statements.
     */
    @Column(name = "participant_id")
    private String participantId;

    @Column(name = "callback_url", length = 500)
    private String callbackUrl;

    /**
     * The name of the environment variable holding the key. Never the key.
     */
    @Column(name = "secret_env_var")
    private String secretEnvVar;

    /** Sandbox or live, where the provider has both. Recorded so nobody mistakes one for the other. */
    @Column(name = "environment_name")
    @Builder.Default
    private String environmentName = "SANDBOX";

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "contact", length = 300)
    private String contact;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Whether every setting this provider needs has been filled in.
     *
     * <p>This is the whole of what can honestly be checked from here. It reads the recorded fields
     * and nothing else — it does not reach the provider, does not read the environment variable and
     * cannot tell a correct key from a typo.
     */
    @Transient
    public boolean isComplete() {
        if (provider == null) {
            return false;
        }
        if (provider.needsEndpoint() && isBlank(endpoint)) {
            return false;
        }
        if (provider.needsParticipantId() && isBlank(participantId)) {
            return false;
        }
        if (provider.needsCallback() && isBlank(callbackUrl)) {
            return false;
        }
        return !isBlank(secretEnvVar);
    }

    /**
     * Whether the environment variable named here is actually set on this server.
     *
     * <p>Only its presence is reported. The value is not read, not logged and not returned, so this
     * can say "the variable is missing" without ever being a way to read a secret back out through a
     * page.
     */
    @Transient
    public boolean isSecretPresent() {
        if (isBlank(secretEnvVar)) {
            return false;
        }
        String value = System.getenv(secretEnvVar.trim());
        return value != null && !value.isBlank();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
