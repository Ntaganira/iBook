/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : PageController.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : Thymeleaf view routing controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/error/404")
    public String notFound() {
        return "error/404";
    }

    @GetMapping("/error/403")
    public String forbidden() {
        return "error/403";
    }
}