/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.enums
 * - File      : DocumentLinkType.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : What record a stored file is attached to
 * </pre>
 */
package com.ntaganira.heritier.ibook.enums;

public enum DocumentLinkType {

    NONE(null),
    CUSTOMER("/customers/"),
    VENDOR("/vendors/"),
    INVOICE("/invoices/"),
    BILL("/bills/"),
    EXPENSE("/purchases/expenses/"),
    PROJECT("/projects/"),
    FIXED_ASSET("/assets/register/"),
    JOURNAL_ENTRY("/journals/");

    private final String path;

    DocumentLinkType(String path) {
        this.path = path;
    }

    public boolean isAttached() {
        return this != NONE;
    }

    /**
     * Where the record lives. Held here rather than built in a template so a route that moves is
     * corrected once — the difference between {@code /invoices} and {@code /sales/invoices} has
     * already cost this codebase a broken link more than once.
     */
    public String linkTo(Long id) {
        return path == null || id == null ? null : path + id;
    }
}
