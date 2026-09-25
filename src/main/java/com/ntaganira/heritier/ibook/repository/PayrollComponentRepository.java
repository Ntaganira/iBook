/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : PayrollComponentRepository.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for payroll allowances and deductions
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.PayrollComponent;
import com.ntaganira.heritier.ibook.enums.PayrollComponentKind;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PayrollComponentRepository extends JpaRepository<PayrollComponent, Long> {

    Optional<PayrollComponent> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    long countByKind(PayrollComponentKind kind);

    long countByKindAndActive(PayrollComponentKind kind, boolean active);

    @Query("select c from PayrollComponent c where c.kind = :kind "
            + "and (:q is null or :q = '' "
            + "   or lower(c.name) like lower(concat('%', :q, '%')) "
            + "   or lower(c.code) like lower(concat('%', :q, '%'))) "
            + "and (:active is null or c.active = :active)")
    Page<PayrollComponent> search(@Param("kind") PayrollComponentKind kind,
                                  @Param("q") String q,
                                  @Param("active") Boolean active,
                                  Pageable pageable);

    List<PayrollComponent> findByActiveTrueOrderBySortOrderAscNameAsc();

    List<PayrollComponent> findByKindAndActiveTrueOrderBySortOrderAscNameAsc(PayrollComponentKind kind);
}
