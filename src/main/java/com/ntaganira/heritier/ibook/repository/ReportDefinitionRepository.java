/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : ReportDefinitionRepository.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for saved report definitions
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.ReportDefinition;
import com.ntaganira.heritier.ibook.enums.ReportBasis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReportDefinitionRepository extends JpaRepository<ReportDefinition, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    long countByBasis(ReportBasis basis);

    @Query("select d from ReportDefinition d where (:q is null or :q = '' "
            + "   or lower(d.name) like lower(concat('%', :q, '%')) "
            + "   or lower(d.description) like lower(concat('%', :q, '%'))) "
            + "   and (:basis is null or d.basis = :basis)")
    Page<ReportDefinition> search(@Param("q") String q,
                                  @Param("basis") ReportBasis basis,
                                  Pageable pageable);

    List<ReportDefinition> findTop10ByOrderByLastRunAtDescIdDesc();
}
