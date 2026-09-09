/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : AppPageRepository.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for application pages
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.AppPage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppPageRepository extends JpaRepository<AppPage, Long> {

    List<AppPage> findAllByOrderByNameAsc();
}