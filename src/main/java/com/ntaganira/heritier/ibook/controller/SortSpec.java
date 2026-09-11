/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : SortSpec.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : Shared sort/dir resolution and list-context wiring for list pages
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import org.springframework.data.domain.Sort;
import org.springframework.ui.Model;

public record SortSpec(String field, String dir, Sort sort) {

    public static SortSpec resolve(String sort, String dir, String defaultField, String... allowed) {
        if (sort == null || sort.isBlank()) {
            sort = defaultField;
        }
        boolean ok = false;
        for (String a : allowed) {
            if (a.equals(sort)) {
                ok = true;
                break;
            }
        }
        if (!ok) {
            sort = defaultField;
        }
        String d = "desc".equalsIgnoreCase(dir) ? "desc" : "asc";
        Sort sortDef = Sort.by(d.equals("desc") ? Sort.Direction.DESC : Sort.Direction.ASC, sort)
                .and(Sort.by(Sort.Direction.DESC, "id"));
        return new SortSpec(sort, d, sortDef);
    }

    public static void addListContext(Model model, String basePath, String filterQuery, SortSpec sp) {
        model.addAttribute("basePath", basePath);
        model.addAttribute("filterQuery", filterQuery);
        model.addAttribute("sort", sp.field());
        model.addAttribute("dir", sp.dir());
    }
}