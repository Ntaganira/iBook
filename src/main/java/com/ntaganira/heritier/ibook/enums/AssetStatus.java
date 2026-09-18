/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.enums
 * - File      : AssetStatus.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Lifecycle of a registered fixed asset
 * </pre>
 */
package com.ntaganira.heritier.ibook.enums;

/**
 * Every value the fixed-asset routes will ever set is declared here from the start. Hibernate writes
 * the check constraint when the table is created and {@code ddl-auto: update} never widens it, so a
 * value added later would fail on any database that already exists.
 */
public enum AssetStatus {

    DRAFT,
    ACTIVE,
    DISPOSED,
    WRITTEN_OFF
}
