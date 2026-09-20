/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : BudgetRepository.java
 * - Date      : 2026. 09. 20.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for budgets
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.Budget;
import com.ntaganira.heritier.ibook.enums.BudgetStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    Optional<Budget> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    @Query("select b from Budget b where (:q is null or :q = '' "
            + "   or lower(b.name) like lower(concat('%', :q, '%')) "
            + "   or lower(b.description) like lower(concat('%', :q, '%'))) "
            + "   and (:status is null or b.status = :status) "
            + "   and (:fiscalYear is null or b.fiscalYear = :fiscalYear)")
    Page<Budget> search(@Param("q") String q,
                        @Param("status") BudgetStatus status,
                        @Param("fiscalYear") Integer fiscalYear,
                        Pageable pageable);

    long countByStatus(BudgetStatus status);

    List<Budget> findAllByOrderByFiscalYearDescNameAsc();

    @Query("select distinct b.fiscalYear from Budget b order by b.fiscalYear desc")
    List<Integer> distinctFiscalYears();

    /** What a comparison defaults to: an approved budget is the one the business agreed on. */
    @Query("select b from Budget b where b.status = "
            + "com.ntaganira.heritier.ibook.enums.BudgetStatus.APPROVED "
            + "order by b.fiscalYear desc, b.id desc")
    List<Budget> approved();
}
