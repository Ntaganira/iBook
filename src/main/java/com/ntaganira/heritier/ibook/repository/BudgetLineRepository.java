/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : BudgetLineRepository.java
 * - Date      : 2026. 09. 20.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for budget lines
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.BudgetLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BudgetLineRepository extends JpaRepository<BudgetLine, Long> {

    List<BudgetLine> findByBudgetIdOrderBySortOrderAscIdAsc(Long budgetId);

    long countByAccountId(Long accountId);
}
