/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.config
 * - File      : GlobalModelAdvice.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : Global controller advice exposing request attributes
 * </pre>
 */
package com.ntaganira.heritier.ibook.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModelAdvice {

    @ModelAttribute("requestURI")
    public String requestURI(HttpServletRequest request) {
        return request.getRequestURI();
    }
}