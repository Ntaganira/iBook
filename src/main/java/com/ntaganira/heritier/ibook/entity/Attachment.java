/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : Attachment.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : JPA entity for a stored file and what it belongs to
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import com.ntaganira.heritier.ibook.enums.DocumentCategory;
import com.ntaganira.heritier.ibook.enums.DocumentLinkType;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Table(name = "attachments")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attachment implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * What the file is called inside the store. Generated, never taken from the upload: a name
     * the browser supplied is attacker-controlled text, and letting it reach the filesystem is how
     * a file ends up written outside the folder it was meant for.
     */
    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;

    /** What the person called it. Kept for display and download only; never used as a path. */
    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    @Builder.Default
    private long sizeBytes = 0L;

    /** Lets a re-upload of the same bytes be pointed out, and a stored file be checked later. */
    @Column(name = "checksum", length = 64)
    private String checksum;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    @Builder.Default
    private DocumentCategory category = DocumentCategory.OTHER;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false)
    @Builder.Default
    private DocumentLinkType linkType = DocumentLinkType.NONE;

    @Column(name = "link_id")
    private Long linkId;

    /**
     * The record's name as it read when the file was attached. Denormalised so the library lists
     * without touching nine other tables, and refreshed when the link is set again.
     */
    @Column(name = "link_label")
    private String linkLabel;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "tags", length = 300)
    private String tags;

    /**
     * A blank form the business reuses — a contract skeleton, a letterhead, a timesheet. Nothing
     * to do with the invoice and email templates under Settings, which format documents the app
     * generates rather than being files anybody downloads.
     */
    @Column(name = "is_template", nullable = false)
    @Builder.Default
    private boolean template = false;

    @Column(name = "download_count", nullable = false)
    @Builder.Default
    private int downloadCount = 0;

    @Column(name = "archived", nullable = false)
    @Builder.Default
    private boolean archived = false;

    @Column(name = "uploaded_by")
    private String uploadedBy;

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

    @Transient
    public boolean isAttached() {
        return linkType != null && linkType.isAttached() && linkId != null;
    }

    @Transient
    public String getLinkHref() {
        return linkType == null ? null : linkType.linkTo(linkId);
    }

    @Transient
    public String getExtension() {
        int dot = originalName == null ? -1 : originalName.lastIndexOf('.');
        return dot < 0 ? "" : originalName.substring(dot + 1).toLowerCase();
    }

    /** Size in kilobytes, since a byte count is not a figure anybody reads off a list. */
    @Transient
    public BigDecimal getSizeKb() {
        return BigDecimal.valueOf(sizeBytes)
                .divide(BigDecimal.valueOf(1024), 1, RoundingMode.HALF_UP);
    }

    /**
     * Whether a browser may show the file rather than download it. Deliberately an allow list: an
     * uploaded HTML or SVG file served inline runs its own script under this application's origin,
     * with this application's session cookie. Anything not on the list is handed over as a
     * download instead, which costs a click and closes that door.
     */
    @Transient
    public boolean isViewableInline() {
        if (contentType == null) {
            return false;
        }
        return switch (contentType.toLowerCase()) {
            case "application/pdf", "image/png", "image/jpeg", "image/gif", "image/webp",
                 "text/plain" -> true;
            default -> false;
        };
    }
}
