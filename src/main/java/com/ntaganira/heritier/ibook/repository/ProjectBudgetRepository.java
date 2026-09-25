/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : ProjectBudgetRepository.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for project budgets
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.ProjectBudget;
import com.ntaganira.heritier.ibook.enums.BudgetStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectBudgetRepository extends JpaRepository<ProjectBudget, Long> {

    Optional<ProjectBudget> findByProjectId(Long projectId);

    boolean existsByProjectId(Long projectId);

    long countByStatus(BudgetStatus status);

    List<ProjectBudget> findAllByOrderByProjectCodeAsc();
}
