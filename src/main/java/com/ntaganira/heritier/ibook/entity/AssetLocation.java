/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : AssetLocation.java
 * - Date      : 2026. 09. 19.
 * - User      : Hntaganira
 * - Desc      : JPA entity for the places fixed assets are kept
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "asset_locations")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetLocation implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    /** Optional, because a location imported from free text has no code to import. */
    @Column(name = "code", unique = true)
    private String code;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "site")
    private String site;

    @Column(name = "address", length = 300)
    private String address;

    @Column(name = "city")
    private String city;

    /** Who is answerable for what is kept here, distinct from an asset's own custodian. */
    @Column(name = "manager")
    private String manager;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

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
