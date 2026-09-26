/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : ExciseDutyRepository.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for excise duties
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.ExciseDuty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExciseDutyRepository extends JpaRepository<ExciseDuty, Long> {

    Optional<ExciseDuty> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    long countByActive(boolean active);

    long countByConfirmed(boolean confirmed);

    @Query("select d from ExciseDuty d where (:q is null or :q = '' "
            + "   or lower(d.name) like lower(concat('%', :q, '%')) "
            + "   or lower(d.code) like lower(concat('%', :q, '%')) "
            + "   or lower(d.appliesTo) like lower(concat('%', :q, '%'))) "
            + "   and (:active is null or d.active = :active)")
    Page<ExciseDuty> search(@Param("q") String q,
                            @Param("active") Boolean active,
                            Pageable pageable);

    List<ExciseDuty> findByActiveTrueOrderByCodeAsc();
}
