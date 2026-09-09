/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : SecuritySettings.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : JPA entity for security settings
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "security_settings")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecuritySettings implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    @Column(name = "two_factor_required", nullable = false)
    @Builder.Default
    private boolean twoFactorRequired = false;

    @Column(name = "password_min_length", nullable = false)
    @Builder.Default
    private int passwordMinLength = 8;

    @Column(name = "require_uppercase", nullable = false)
    @Builder.Default
    private boolean requireUppercase = true;

    @Column(name = "require_number", nullable = false)
    @Builder.Default
    private boolean requireNumber = true;

    @Column(name = "require_special_char", nullable = false)
    @Builder.Default
    private boolean requireSpecialChar = false;

    @Column(name = "password_expiry_days", nullable = false)
    @Builder.Default
    private int passwordExpiryDays = 90;

    @Column(name = "max_login_attempts", nullable = false)
    @Builder.Default
    private int maxLoginAttempts = 5;

    @Column(name = "lockout_minutes", nullable = false)
    @Builder.Default
    private int lockoutMinutes = 15;

    @Column(name = "session_timeout_minutes", nullable = false)
    @Builder.Default
    private int sessionTimeoutMinutes = 15;

    @Column(name = "allow_public_signup", nullable = false)
    @Builder.Default
    private boolean allowPublicSignup = true;

    @Column(name = "default_role", nullable = false)
    @Builder.Default
    private String defaultRole = "USER";

    @Column(name = "ip_whitelist_enabled", nullable = false)
    @Builder.Default
    private boolean ipWhitelistEnabled = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}