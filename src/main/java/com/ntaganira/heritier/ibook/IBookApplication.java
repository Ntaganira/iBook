/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook
 * - File      : IBookApplication.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : Ebook Online Spring Boot application entry point
 * </pre>
 */
package com.ntaganira.heritier.ibook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IBookApplication {

    public static void main(String[] args) {
        SpringApplication.run(IBookApplication.class, args);
    }
}